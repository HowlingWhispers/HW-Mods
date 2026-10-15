import http from 'node:http';
import {createHash, timingSafeEqual, randomUUID} from 'node:crypto';
import {createReadStream} from 'node:fs';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import pg from 'pg';
import yauzl from 'yauzl';
import {validateAnnouncement, validateManifest, trustedReleaseUrl} from './validation.mjs';

const {Pool}=pg;
const MAX_JAR=32*1024*1024;
const MAX_BODY=8192;
const HOST=process.env.HOST||'127.0.0.1';
const PORT=Number(process.env.PORT||8795);
const STORAGE=path.resolve(process.env.STORAGE_ROOT||'./objects');
const TARGET='26.4-snapshot-3';
const USER_AGENT='HOWL-Official-Registry/0.1';
const pool=new Pool({connectionString:process.env.DATABASE_URL,max:8,
  connectionTimeoutMillis:5000,statement_timeout:10000});

class ApiError extends Error{constructor(status,message){super(message);this.status=status;}}
function json(res,status,value){
 const body=JSON.stringify(value);
 res.writeHead(status,{'Content-Type':'application/json; charset=utf-8',
   'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
 res.end(body);
}
function authorized(header){
 const expected=process.env.HOWL_REGISTRY_PUBLISH_TOKEN;
 if(!expected||expected.length<32)return false;
 const supplied=header?.startsWith('Bearer ')?header.slice(7):'';
 const hash=x=>createHash('sha256').update(x).digest();
 return timingSafeEqual(hash(supplied),hash(expected));
}
async function receiveJson(req){
 if(!req.headers['content-type']?.toLowerCase().startsWith('application/json'))
   throw new ApiError(415,'Expected application/json');
 let total=0; const chunks=[];
 for await (const chunk of req){
   total+=chunk.length;
   if(total>MAX_BODY)throw new ApiError(413,'Announcement too large');
   chunks.push(chunk);
 }
 try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}
 catch{throw new ApiError(400,'Invalid JSON');}
}
async function getJson(url){
 const response=await fetch(url,{headers:{'User-Agent':USER_AGENT,'Accept':'application/vnd.github+json'},signal:AbortSignal.timeout(20000)});
 if(!response.ok)throw new ApiError(502,'Cannot verify official GitHub release');
 return response.json();
}
async function downloadVerifiedAsset(url,expectedSha){
 const response=await fetch(url,{headers:{'User-Agent':USER_AGENT},signal:AbortSignal.timeout(45000)});
 if(!response.ok)throw new ApiError(502,'Official release download failed');
 const host=new URL(response.url).hostname;
 if(!['github.com','release-assets.githubusercontent.com','objects.githubusercontent.com'].includes(host))
   throw new ApiError(502,'Untrusted download redirect');
 if(Number(response.headers.get('content-length')||0)>MAX_JAR)
   throw new ApiError(413,'JAR exceeds registry limit');
 const chunks=[];let count=0;
 for await (const chunk of response.body){
   count+=chunk.length;
   if(count>MAX_JAR)throw new ApiError(413,'JAR exceeds registry limit');
   chunks.push(chunk);
 }
 if(!count)throw new ApiError(422,'Empty release asset');
 const data=Buffer.concat(chunks);
 const digest=createHash('sha256').update(data).digest('hex');
 if(digest!==expectedSha)throw new ApiError(422,'Published JAR SHA-256 mismatch');
 return data;
}
async function inspectJar(buffer,announcement){
 return new Promise((resolve,reject)=>{
   yauzl.fromBuffer(buffer,{lazyEntries:true,validateEntrySizes:true},(openError,zip)=>{
     if(openError)return reject(new ApiError(422,'Invalid JAR archive'));
     let manifest=null;let count=0;let done=false;
     const classes=new Set();
     function fail(message){if(done)return;done=true;zip.close();reject(new ApiError(422,message));}
     zip.on('error',()=>fail('Unreadable JAR archive'));
     zip.on('entry',entry=>{
       if(++count>12000)return fail('JAR has too many archive entries');
       if(entry.fileName.startsWith('/')||entry.fileName.split('/').includes('..'))
         return fail('Unsafe JAR entry');
       if(entry.fileName.endsWith('.class'))classes.add(entry.fileName);
       if(entry.fileName!=='coda.mod.json'){zip.readEntry();return;}
       if(manifest||entry.uncompressedSize===0||entry.uncompressedSize>8192)
         return fail('Duplicate or invalid coda.mod.json');
       zip.openReadStream(entry,(error,stream)=>{
         if(error)return fail('Unreadable mod manifest');
         const chunks=[];let received=0;
         stream.on('error',()=>fail('Unreadable compressed manifest'));
         stream.on('data',chunk=>{
           received+=chunk.length;
           if(received>8192)return fail('Oversized manifest');
           chunks.push(chunk);
         });
         stream.on('end',()=>{
           if(done)return;
           try{manifest=JSON.parse(Buffer.concat(chunks).toString('utf8'));}
           catch{return fail('Malformed coda.mod.json');}
           zip.readEntry();
         });
       });
     });
     zip.on('end',()=>{
       if(done)return;done=true;
       if(!manifest)return reject(new ApiError(422,'JAR lacks coda.mod.json'));
       try{
         const entry=typeof manifest.entrypoint==='string'
           ?manifest.entrypoint.replaceAll('.','/')+'.class':'';
         resolve(validateManifest(manifest,announcement,classes.has(entry)?entry:''));
       }catch(error){reject(new ApiError(422,error.message));}
     });
     zip.readEntry();
   });
 });
}
async function fetchOfficialJar(announcement){
 const url='https://api.github.com/repos/HowlingWhispers/HW-Mods/releases/tags/'+
   encodeURIComponent(announcement.tag);
 const release=await getJson(url);
 if(release.draft||release.tag_name!==announcement.tag)
   throw new ApiError(422,'Release is not public and immutable');
 const exactUrl=trustedReleaseUrl(announcement.tag,announcement.assetName);
 const asset=(release.assets||[]).find(item=>
   item.name===announcement.assetName&&item.browser_download_url===exactUrl);
 if(!asset)throw new ApiError(422,'JAR missing from approved GitHub release');
 if(asset.digest&&asset.digest!==('sha256:'+announcement.sha256))
   throw new ApiError(422,'GitHub release digest disagrees');
 const bytes=await downloadVerifiedAsset(exactUrl,announcement.sha256);
 const manifest=await inspectJar(bytes,announcement);
 return {bytes,manifest,exactUrl};
}
async function stageObject(digest,data){
 await fs.mkdir(STORAGE,{recursive:true,mode:0o750});
 const target=path.join(STORAGE,digest+'.jar');
 const temp=path.join(STORAGE,'.'+randomUUID()+'.tmp');
 await fs.writeFile(temp,data,{flag:'wx',mode:0o640});
 try{
   // link (not rename) keeps already-published content-addressed objects immutable.
   try{await fs.link(temp,target);}
   catch(error){if(error.code!=='EEXIST')throw error;}
 }finally{await fs.unlink(temp).catch(()=>{});}
 return target;
}
function publicRelease(row){
 return {id:row.mod_id,name:row.display_name,version:row.version,
   channel:row.channel,required:row.required,minecraft:row.minecraft,
   minimumLoader:row.minimum_loader,depends:row.depends,description:row.description,
   sha256:row.sha256,bytes:Number(row.size_bytes),tag:row.release_tag,
   source:'HowlingWhispers/HW-Mods',
   downloadUrl:'/v1/assets/'+row.sha256};
}
async function publish(req,res){
 if(!process.env.HOWL_REGISTRY_PUBLISH_TOKEN)
   throw new ApiError(503,'Registry publication is not configured');
 if(!authorized(req.headers.authorization))
   throw new ApiError(401,'Unauthorized publisher');
 const raw=await receiveJson(req);let item;
 try{item=validateAnnouncement(raw);}catch(error){throw new ApiError(400,error.message);}
 const allowedRequired=(process.env.REQUIRED_MOD_IDS||'howl_sorting_pipes').split(',').map(x=>x.trim());
 if(item.required&&!allowedRequired.includes(item.slug.replaceAll('-','_')))
   throw new ApiError(422,'Mod is not approved as required');
 const {bytes,manifest,exactUrl}=await fetchOfficialJar(item);
 const client=await pool.connect();
 try{
   await client.query('BEGIN');
   if(manifest.depends.length){
     const found=await client.query('SELECT DISTINCT mod_id FROM mod_releases WHERE channel=$1 AND mod_id=ANY($2::text[])',
       ['nightly',manifest.depends]);
     if(found.rowCount!==manifest.depends.length)
       throw new ApiError(422,'Unmet H.O.W.L. mod dependencies');
   }
   const existing=await client.query(
     'SELECT sha256 FROM mod_releases WHERE mod_id=$1 AND channel=$2 AND release_tag=$3 FOR UPDATE',
     [manifest.id,'nightly',item.tag]);
   if(existing.rowCount){
     if(existing.rows[0].sha256!==item.sha256)
       throw new ApiError(409,'Immutable release tag conflicts with previous asset');
     await client.query('COMMIT');
     return json(res,200,{ok:true,id:manifest.id,unchanged:true});
   }
   await stageObject(item.sha256,bytes);
   await client.query(`INSERT INTO mod_releases
     (mod_id,display_name,version,release_tag,channel,minecraft,minimum_loader,required,
       depends,description,sha256,asset_name,size_bytes,source_url)
     VALUES ($1,$2,$3,$4,'nightly',$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
     [manifest.id,item.displayName,manifest.version,item.tag,TARGET,item.minimumLoader,item.required,
       manifest.depends,item.description,item.sha256,item.assetName,bytes.length,exactUrl]);
   await client.query('COMMIT');
   return json(res,201,{ok:true,id:manifest.id,sha256:item.sha256});
 }catch(error){await client.query('ROLLBACK');throw error;}
 finally{client.release();}
}
async function route(req,res){
 const url=new URL(req.url,'http://127.0.0.1');
 if(url.pathname==='/healthz'&&req.method==='GET'){
   await pool.query('SELECT 1');return json(res,200,{ok:true});
 }
 if(url.pathname==='/v1/mods'&&req.method==='GET'){
   const channel=url.searchParams.get('channel')||'nightly';
   if(!['nightly','stable'].includes(channel))throw new ApiError(400,'Invalid channel');
   if(url.searchParams.has('minecraft')&&url.searchParams.get('minecraft')!==TARGET)
     return json(res,200,{schema:1,mods:[]});
   const data=await pool.query(`SELECT DISTINCT ON (mod_id) * FROM mod_releases
     WHERE channel=$1 ORDER BY mod_id,approved_at DESC,id DESC`,[channel]);
   return json(res,200,{schema:1,minecraft:TARGET,channel,mods:data.rows.map(publicRelease)});
 }
 const detail=/^\/v1\/mods\/([a-z][a-z0-9_]{2,80})$/.exec(url.pathname);
 if(detail&&req.method==='GET'){
   const data=await pool.query(`SELECT * FROM mod_releases
     WHERE mod_id=$1 ORDER BY approved_at DESC,id DESC LIMIT 30`,[detail[1]]);
   if(!data.rowCount)throw new ApiError(404,'Mod not found');
   return json(res,200,{id:detail[1],versions:data.rows.map(publicRelease)});
 }
 const asset=/^\/v1\/assets\/([0-9a-f]{64})$/.exec(url.pathname);
 if(asset&&(req.method==='GET'||req.method==='HEAD')){
   const data=await pool.query('SELECT size_bytes FROM mod_releases WHERE sha256=$1 LIMIT 1',[asset[1]]);
   if(!data.rowCount)throw new ApiError(404,'Asset not published');
   const file=path.join(STORAGE,asset[1]+'.jar');
   try{await fs.access(file);}catch{throw new ApiError(503,'Published asset temporarily unavailable');}
   res.writeHead(200,{'Content-Type':'application/java-archive',
     'Content-Length':String(data.rows[0].size_bytes),
     'Content-Disposition':'attachment; filename="howl-mod-'+asset[1]+'.jar"',
     'Cache-Control':'public, max-age=31536000, immutable',
     'ETag':'"'+asset[1]+'"','X-Content-Type-Options':'nosniff'});
   if(req.method==='HEAD')return res.end();
   return createReadStream(file).pipe(res);
 }
 if(url.pathname==='/v1/publish'&&req.method==='POST')return publish(req,res);
 throw new ApiError(404,'Not found');
}
async function main(){
 if(!process.env.DATABASE_URL)throw new Error('DATABASE_URL is required');
 if(!Number.isInteger(PORT)||PORT<1||PORT>65535)throw new Error('Invalid PORT');
 const schema=await fs.readFile(path.join(path.dirname(fileURLToPath(import.meta.url)),'schema.sql'),'utf8');
 await pool.query(schema);
 http.createServer((req,res)=>{
   route(req,res).catch(error=>{
     const status=error.status||500;
     if(status===500||status===502)console.error('H.O.W.L. registry:',error.message);
     if(!res.headersSent)json(res,status,{error:status===500?'Internal registry error':error.message});
     else res.destroy(error);
   });
 }).listen(PORT,HOST,()=>console.log('H.O.W.L. registry listening on '+HOST+':'+PORT));
}
main().catch(error=>{console.error(error);process.exitCode=1;pool.end();});
