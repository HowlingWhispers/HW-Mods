"""Deterministic, original models referencing Minecraft's built-in textures."""
import json,zipfile,pathlib
from guidebook import book_components, unlock_advancement
root=pathlib.Path(__file__).parent; assets={}; namespace='howl_sorting_pipes'
def put(path,content): assets[path]=json.dumps(content,indent=2).encode()
def box(lo,hi,texture):
    return {'from':lo,'to':hi,'faces':{d:{'texture':'#'+texture} for d in ['north','south','east','west','up','down']}}
colors={'extractor_pipe':'oak_planks','transport_pipe':'iron_block','sorting_pipe':'diamond_block','round_robin':'copper_block','equalizer':'gold_block'}
# The original Pixel Studio PNG has a 6x6 opaque design centered in 16x16 UV space.
# The existing 5..11 core cube samples exactly that area on each face.
center_path=root/'textures/block/pipe_center.png'
center_png=center_path.read_bytes()
if not (center_png[:8]==bytes([137,80,78,71,13,10,26,10])
        and center_png[12:16]==b'IHDR'
        and int.from_bytes(center_png[16:20],'big')==16
        and int.from_bytes(center_png[20:24],'big')==16
        and center_png[24]==8 and center_png[25]==6):
    raise ValueError('Pipe center texture must be a 16x16 RGBA PNG')
assets[f'assets/{namespace}/textures/block/pipe_center.png']=center_png
for name,texture in colors.items():
    base=f'assets/{namespace}/'
    if name.endswith('_pipe'):
        core_texture=f'{namespace}:block/pipe_center' if name=='transport_pipe' else 'minecraft:block/'+texture
        put(base+f'models/block/{name}_core.json',{'textures':{'body':core_texture,'particle':'minecraft:block/'+texture},'elements':[box([5,5,5],[11,11,11],'body')]})
        segments={'east':([11,5,5],[16,11,11]),'west':([0,5,5],[5,11,11]),'up':([5,11,5],[11,16,11]),'down':([5,0,5],[11,5,11]),'south':([5,5,11],[11,11,16]),'north':([5,5,0],[11,11,5])}
        parts=[{'apply':{'model':f'{namespace}:block/{name}_core'}}]
        for face,(lo,hi) in segments.items():
            put(base+f'models/block/{name}_{face}.json',{'textures':{'body':'minecraft:block/'+texture},'elements':[box(lo,hi,'body')]})
            parts.append({'when':{face:'true'},'apply':{'model':f'{namespace}:block/{name}_{face}'}})
        put(base+f'blockstates/{name}.json',{'multipart':parts})
        put(base+f'models/item/{name}.json',{'parent':f'{namespace}:block/{name}_core'})
    else:
        put(base+f'blockstates/{name}.json',{'variants':{'':{'model':f'{namespace}:block/{name}'}}})
        put(base+f'models/block/{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/'+texture}})
        put(base+f'models/item/{name}.json',{'parent':f'{namespace}:block/{name}'})
    put(base+f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'{namespace}:item/{name}'}})
put(f'assets/{namespace}/lang/en_us.json',{f'{kind}.{namespace}.{name}':name.replace('_',' ').title() for kind in ['block','item'] for name in colors})
def writezip(path,files):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,value in sorted(files.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0)); entry.compress_type=zipfile.ZIP_DEFLATED; z.writestr(entry,value)
pathlib.Path(root/'dist').mkdir(exist_ok=True)
meta={'pack':{'description':'H.O.W.L. Sorting Pipes models','min_format':[100,0],'max_format':[100,0]}}
writezip(root/'dist/HOWL-Sorting-Pipes-Resources.zip',assets|{'pack.mcmeta':json.dumps(meta).encode()})
recipes={}
for name,ingredient,count in [('extractor_pipe','minecraft:oak_planks',4),('transport_pipe','minecraft:iron_ingot',8),('sorting_pipe','minecraft:diamond',4),('round_robin','minecraft:copper_ingot',1),('equalizer','minecraft:gold_ingot',1)]:
    recipe={'type':'minecraft:crafting_shaped','pattern':[' G ','GIG',' G '],'key':{'G':'minecraft:glass','I':ingredient},'result':{'id':f'{namespace}:{name}','count':count}}
    recipes[f'data/{namespace}/recipe/{name}.json']=json.dumps(recipe,indent=2).encode()
for name in colors:
    recipes[f'data/{namespace}/loot_table/blocks/{name}.json']=json.dumps({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{namespace}:{name}','condition':{'type':'minecraft:survives_explosion'}}]}]}).encode()
recipes[f'data/{namespace}/advancement/guide_unlock.json']=json.dumps(unlock_advancement(),indent=2).encode()
recipes[f'data/{namespace}/loot_table/guide.json']=json.dumps({'type':'minecraft:advancement_reward','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'minecraft:written_book','modifier':{'type':'minecraft:set_components','components':book_components()}}]}]},indent=2).encode()
meta={'pack':{'description':'H.O.W.L. Sorting Pipes guide, recipes and block drops','min_format':123,'max_format':123}}
writezip(root/'dist/HOWL-Sorting-Pipes-Recipes.zip',recipes|{'pack.mcmeta':json.dumps(meta).encode()})
