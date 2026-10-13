"""Short, deliberately paginated text for Minecraft's ordinary written-book UI."""
PAGES = [('Sorting Pipes',
  'Your first storage network\n'
  '\n'
  "You earned this guide after Acquire Hardware and Isn't It Iron Pick.\n"
  '\n'
  'Start with two chests, an Extractor Pipe and some Transport Pipes.'),
 ('Gather supplies',
  'Bring glass blocks, oak planks and iron ingots.\n'
  '\n'
  'Copper adds Round Robin. Gold adds Equalizer. A diamond makes Sorting Pipes.\n'
  '\n'
  'Recipes begin on page 11.'),
 ('Your first line',
  'Place an oak Extractor Pipe beside your source chest.\n'
  '\n'
  'Connect iron Transport Pipes from it to an output chest.\n'
  '\n'
  'Put items in the source. Touching faces connect; diagonals do not.'),
 ('Input side',
  'One chest/barrel beside the extractor is selected automatically.\n'
  '\n'
  'If several touch it:\n'
  '/howlpipes input 1 64 0 west\n'
  '\n'
  "Use your extractor's F3 coordinates instead of 1 64 0."),
 ('Branch sorting',
  'Put diamond Sorting Pipes on branches.\n'
  '\n'
  'Whitelist: only listed items may pass.\n'
  '\n'
  'Blacklist: listed items may NOT pass.\n'
  '\n'
  'Unconfigured pipes allow everything.'),
 ('Allow only iron',
  'For a pipe at 3 64 1:\n'
  '\n'
  '/howlpipes filter 3 64 1 whitelist minecraft:iron_ingot\n'
  '\n'
  "Replace 3 64 1 with your pipe's targeted-block coordinates from F3."),
 ('Block dirt',
  'For a pipe at 3 64 1:\n'
  '\n'
  '/howlpipes filter 3 64 1 blacklist minecraft:dirt\n'
  '\n'
  "Use your pipe's coordinates. Multiple exact item IDs can be separated by commas."),
 ('Round Robin',
  'Place the copper block beside the EXTRACTOR.\n'
  '\n'
  'It sends one item to A, then B, then C, repeating.\n'
  '\n'
  'Full or incompatible outputs are skipped. Placing the block activates it.'),
 ('Equalizer',
  'Place the gold block beside the EXTRACTOR.\n'
  '\n'
  'Items go to the output holding the fewest of that item.\n'
  '\n'
  '20 iron in A, 0 in B? New iron goes to B. It does not move old items.'),
 ('Choose one mode',
  'Use Round Robin OR Equalizer. Both together pause the extractor.\n'
  '\n'
  'The attachment must touch an extractor face. It is a separate block, not a cover.\n'
  '\n'
  'Filters still apply.'),
 ('Recipe layout',
  'All recipes use FOUR GLASS BLOCKS.\n'
  '\n'
  'Top: glass in centre.\n'
  'Middle: glass, ingredient, glass.\n'
  'Bottom: glass in centre.\n'
  '\n'
  'Corners are empty. The next pages name the centre ingredient.'),
 ('Extractor recipe',
  'Centre: 1 OAK PLANK\n'
  'Outside: 4 GLASS BLOCKS\n'
  'Result: 4 Extractor Pipes\n'
  '\n'
  'Use an oak pipe at your source chest or barrel. It pulls items into the network.'),
 ('Transport recipe',
  'Centre: 1 IRON INGOT\n'
  'Outside: 4 GLASS BLOCKS\n'
  'Result: 8 Transport Pipes\n'
  '\n'
  'Iron pipes connect the extractor to output storage. Branches and turns work.'),
 ('Sorting recipe',
  'Centre: 1 DIAMOND\n'
  'Outside: 4 GLASS BLOCKS\n'
  'Result: 4 Sorting Pipes\n'
  '\n'
  'Diamond pipes filter a route. Configure each one with a whitelist or blacklist command.'),
 ('Round Robin recipe',
  'Centre: 1 COPPER INGOT\n'
  'Outside: 4 GLASS BLOCKS\n'
  'Result: 1 Round Robin\n'
  '\n'
  'Place beside the extractor. It takes turns between outputs, one item at a time.'),
 ('Equalizer recipe',
  'Centre: 1 GOLD INGOT\n'
  'Outside: 4 GLASS BLOCKS\n'
  'Result: 1 Equalizer\n'
  '\n'
  'Place beside the extractor. It balances the count of each incoming item, not total chest fullness.'),
 ('Nothing moving?',
  'Check the source side, filters and output room. Every filter on a route must allow the item.\n'
  '\n'
  'Use only ONE routing attachment.\n'
  '\n'
  'Use ordinary single chests or barrels to start.'),
 ('Speed and range',
  'No engine is needed. Pipes move one item per pulse, about 1.25 items/second.\n'
  '\n'
  'Items stay in the source until delivery.\n'
  '\n'
  'Stay near the network: unloaded chunks cannot transfer.'),
 ('Check settings',
  'Check your pipe:\n'
  '/howlpipes show 3 64 1\n'
  '\n'
  'Allow all items again:\n'
  '/howlpipes clear 3 64 1\n'
  '\n'
  'Network status:\n'
  '/howlpipes status'),
 ('More help',
  'Filters use commands.\n'
  '\n'
  '/howlpipes help setup\n'
  '\n'
  '/howlpipes help filters\n'
  '\n'
  '/howlpipes help attachments\n'
  '\n'
  'Settings stay with your world.'),
 ('Advancement tabs',
  'Tabs open as you progress.\n'
  '\n'
  'Pick up a crafting table for the Minecraft tree. Eating starts Husbandry.\n'
  '\n'
  'Acquire Hardware: get iron.\n'
  "Isn't It Iron Pick: get an iron pickaxe.")]

def book_components():
    return {
        'minecraft:written_book_content': {
            'title': 'Sorting Pipes Guide', 'author': 'H.O.W.L.', 'generation': 0,
            'resolved': True,
            'pages': [{'text': title+'\n\n', 'bold': True, 'extra': [{'text': text, 'bold': False}]} for title,text in PAGES],
        },
        'minecraft:custom_name': {'text': 'Sorting Pipes Guide', 'italic': False},
        'minecraft:custom_data': {'howl_sorting_pipes_guide': True},
    }

def unlock_advancement():
    return {
        'criteria': {'iron_ready': {
            'trigger': 'minecraft:tick',
            'conditions': {'player': {
                'type': 'minecraft:entity_properties', 'entity': 'this',
                'predicate': {'minecraft:type_specific/player': {'advancements': {
                    'minecraft:story/smelt_iron': True, 'minecraft:story/iron_tools': True,
                }}},
            }},
        }},
        'requirements': [['iron_ready']],
        'rewards': {'loot': ['howl_sorting_pipes:guide'], 'recipes': [
            'howl_sorting_pipes:'+name for name in ['extractor_pipe','transport_pipe','sorting_pipe','round_robin','equalizer']
        ]},
        'sends_telemetry_event': False,
    }
