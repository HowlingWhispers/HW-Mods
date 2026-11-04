// Pure validation shared by the publisher and unit tests.
const releaseJar=/^([a-z][a-z0-9-]*)-(\d+\.\d+\.\d+(?:-[a-z0-9.-]+)?)\.jar$/;
const tagPattern=/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/;
const shaPattern=/^[0-9a-f]{64}$/;
const versionPattern=/^\d+\.\d+\.\d+(?:-[a-z0-9.-]+)?$/;
const modIdPattern=/^[a-z][a-z0-9_]{2,80}$/;
const classPattern=/^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+$/;

export function trustedReleaseUrl(tag,assetName){
 if(!tagPattern.test(tag)||!releaseJar.test(assetName))throw new Error('Invalid release path');
 return 'https://github.com/HowlingWhispers/HW-Mods/releases/download/'+
   encodeURIComponent(tag)+'/'+encodeURIComponent(assetName);
}
export function validateAnnouncement(value){
 if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('Invalid announcement');
 if(value.repository!=='HW-Mods')throw new Error('Untrusted repository');
 if(value.channel!=='nightly')throw new Error('Only Nightly CI publications are accepted');
 if(typeof value.tag!=='string'||!tagPattern.test(value.tag))throw new Error('Invalid tag');
 if(typeof value.assetName!=='string')throw new Error('Missing asset name');
 const match=releaseJar.exec(value.assetName);
 if(!match)throw new Error('Invalid H.O.W.L. JAR name');
 if(typeof value.sha256!=='string'||!shaPattern.test(value.sha256))throw new Error('Invalid SHA-256');
 if(typeof value.minimumLoader!=='string'||!versionPattern.test(value.minimumLoader))
   throw new Error('Invalid minimum H.O.W.L. version');
 if(typeof value.displayName!=='string'||!value.displayName.trim()||value.displayName.length>120)
   throw new Error('Invalid display name');
 if(value.description!==undefined&&(typeof value.description!=='string'||value.description.length>1000))
   throw new Error('Invalid description');
 if(typeof value.required!=='boolean')throw new Error('Required flag must be explicit');
 trustedReleaseUrl(value.tag,value.assetName);
 return {...value,slug:match[1],version:match[2],description:value.description||''};
}
export function validateManifest(meta,announcement,classEntry){
 const item=validateAnnouncement(announcement);
 if(!meta||typeof meta!=='object'||meta.schema!==1)
   throw new Error('Invalid H.O.W.L. mod manifest');
 if(meta.id!==item.slug.replaceAll('-','_')||!modIdPattern.test(meta.id))
   throw new Error('Unexpected mod ID');
 if(meta.version!==item.version)throw new Error('JAR version does not match published filename');
 if(meta.minecraft!=='26.4-snapshot-3')throw new Error('Incompatible Minecraft version');
 if(typeof meta.entrypoint!=='string'||!classPattern.test(meta.entrypoint))
   throw new Error('Invalid entrypoint');
 if(classEntry!==meta.entrypoint.replaceAll('.','/')+'.class')
   throw new Error('Missing matching entrypoint class');
 const deps=meta.depends||[];
 if(!Array.isArray(deps)||deps.length>40||deps.some(dep=>
   typeof dep!=='string'||!modIdPattern.test(dep)||dep===meta.id)||
   new Set(deps).size!==deps.length)
   throw new Error('Invalid declared mod dependencies');
 return {id:meta.id,name:item.displayName,version:meta.version,
   minecraft:meta.minecraft,entrypoint:meta.entrypoint,depends:deps};
}
