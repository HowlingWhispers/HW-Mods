import test from 'node:test';
import assert from 'node:assert/strict';
import {validateAnnouncement, validateManifest, trustedReleaseUrl} from '../validation.mjs';

const good={repository:'HW-Mods',tag:'sorting-pipes-v1.0.3',
 assetName:'howl-sorting-pipes-1.0.3.jar',sha256:'a'.repeat(64),
 channel:'nightly',minimumLoader:'0.0.41',required:true,
 displayName:'H.O.W.L. Sorting Pipes',description:'Sorting and transport pipes'};
const meta={schema:1,id:'howl_sorting_pipes',name:'H.O.W.L. Sorting Pipes',
 version:'1.0.3',minecraft:'26.4-snapshot-3',
 entrypoint:'dev.howlingwhispers.sortingpipes.SortingPipesMod',depends:[]};
test('accepts official Nightly announcement',()=>{
 assert.equal(validateAnnouncement(good).slug,'howl-sorting-pipes');
 assert.equal(validateManifest(meta,good,'dev/howlingwhispers/sortingpipes/SortingPipesMod.class').id,'howl_sorting_pipes');
 assert.equal(trustedReleaseUrl('sorting-pipes-v1.0.3','howl-sorting-pipes-1.0.3.jar'),
 'https://github.com/HowlingWhispers/HW-Mods/releases/download/sorting-pipes-v1.0.3/howl-sorting-pipes-1.0.3.jar');
});
test('rejects unknown publisher and suspicious tag and asset',()=>{
 for(const change of [{repository:'evil'},{tag:'../../evil'},{assetName:'../../oops.jar'},
 {assetName:'howl-sorting-pipes-1.0.3.zip'},{sha256:'bad'},{channel:'stable'}])
 assert.throws(()=>validateAnnouncement({...good,...change}));
});
test('rejects altered manifest identity and missing class',()=>{
 for(const change of [{id:'bad'},{version:'1.0.2'},{minecraft:'other'}, {entrypoint:'bad/../../class'},{depends:['Bad Dependency']}])
 assert.throws(()=>validateManifest({...meta,...change},good,'dev/howlingwhispers/sortingpipes/SortingPipesMod.class'));
 assert.throws(()=>validateManifest(meta,good,'wrong/Entry.class'));
});
