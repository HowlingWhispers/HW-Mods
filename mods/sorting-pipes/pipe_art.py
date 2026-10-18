"""Standard-library PNG artwork generator for seamless H.O.W.L. pipe junctions."""
import struct
import zlib

PNG_SIG = b'\x89PNG\r\n\x1a\n'
DIRECTIONS = ('north', 'south', 'east', 'west', 'up', 'down')
# Implicit Minecraft block-model face UV orientation on a [5,5,5]..[11,11,11] cube.
# Each tuple is left, right, top, bottom directions on the visible face.
FACE_EDGES = {
    'north': ('east', 'west', 'up', 'down'),
    'south': ('west', 'east', 'up', 'down'),
    'east': ('south', 'north', 'up', 'down'),
    'west': ('north', 'south', 'up', 'down'),
    'up': ('west', 'east', 'north', 'south'),
    'down': ('west', 'east', 'south', 'north'),
}


def unpack_rgba_png(data):
    """Read the Pixel Studio 16x16 RGBA export without introducing pip dependencies."""
    if not data.startswith(PNG_SIG):
        raise ValueError('Not a PNG')
    pointer, packed, dims = len(PNG_SIG), bytearray(), None
    while pointer < len(data):
        if pointer + 12 > len(data):
            raise ValueError('Truncated PNG')
        length = int.from_bytes(data[pointer:pointer + 4], 'big')
        kind = data[pointer + 4:pointer + 8]
        chunk = data[pointer + 8:pointer + 8 + length]
        if pointer + 12 + length > len(data):
            raise ValueError('Truncated PNG chunk')
        expected_crc = int.from_bytes(data[pointer + 8 + length:pointer + 12 + length], 'big')
        if zlib.crc32(kind + chunk) != expected_crc:
            raise ValueError('PNG checksum mismatch')
        if kind == b'IHDR':
            dims = struct.unpack('>IIBBBBB', chunk)
        elif kind == b'IDAT':
            packed.extend(chunk)
        elif kind == b'IEND':
            break
        pointer += length + 12
    if dims != (16, 16, 8, 6, 0, 0, 0):
        raise ValueError('Pipe texture must be a noninterlaced 16x16 RGBA PNG')
    raw = zlib.decompress(packed)
    pitch = 16 * 4
    rows, previous, pos = [], bytearray(pitch), 0
    for _ in range(16):
        filter_type = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + pitch])
        if len(line) != pitch:
            raise ValueError('Truncated PNG pixel row')
        pos += pitch + 1
        for i in range(pitch):
            left = line[i - 4] if i >= 4 else 0
            above = previous[i]
            upperleft = previous[i - 4] if i >= 4 else 0
            if filter_type == 1:
                line[i] = (line[i] + left) & 255
            elif filter_type == 2:
                line[i] = (line[i] + above) & 255
            elif filter_type == 3:
                line[i] = (line[i] + (left + above) // 2) & 255
            elif filter_type == 4:
                p = left + above - upperleft
                distances = (abs(p - left), abs(p - above), abs(p - upperleft))
                predictor = (left, above, upperleft)[distances.index(min(distances))]
                line[i] = (line[i] + predictor) & 255
            elif filter_type != 0:
                raise ValueError('Unsupported PNG filter')
        rows.append([tuple(line[i:i + 4]) for i in range(0, pitch, 4)])
        previous = line
    if pos != len(raw):
        raise ValueError('Unexpected trailing PNG pixels')
    return rows


def pack_rgba_png(rows):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))
    pixels = b''.join(b'\0' + b''.join(bytes(pixel) for pixel in row) for row in rows)
    return (PNG_SIG + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b''))


def make_art(original):
    """Preserve the original end-cap; derive matching arms and joined-edge faces."""
    rows = unpack_rgba_png(original)
    for y in range(16):
        for x in range(16):
            if bool(rows[y][x][3]) != (5 <= x <= 10 and 5 <= y <= 10):
                raise ValueError('Pipe center must be an opaque 6x6 square at pixels 5..10')
    # Sample metal shades from the actual player's art, not from vanilla iron.
    dark, mid, bright = rows[5][5], rows[6][6], rows[6][8]

    def rail(coord):
        return dark if coord in (5, 10) else mid if coord in (6, 9) else bright

    files = {'pipe_center.png': original}
    transparent = (0, 0, 0, 0)
    # Along the pipe axis the body is continuous. Across its width, dark rails
    # follow the same one-pixel border as the Pixel Studio center.
    files['pipe_arm_side_h.png'] = pack_rgba_png([
        [rail(y) if 5 <= y <= 10 else transparent for _ in range(16)] for y in range(16)])
    files['pipe_arm_side_v.png'] = pack_rgba_png([
        [rail(x) if 5 <= x <= 10 else transparent for x in range(16)] for _ in range(16)])
    for mask in range(1, 16):
        changed = [row.copy() for row in rows]
        for coord in range(5, 11):
            if mask & 1:
                changed[coord][5] = rail(coord)   # connected at left edge
            if mask & 2:
                changed[coord][10] = rail(coord)  # connected at right edge
            if mask & 4:
                changed[5][coord] = rail(coord)   # connected at upper edge
            if mask & 8:
                changed[10][coord] = rail(coord)  # connected at lower edge
        files[f'pipe_center_joint_{mask:02x}.png'] = pack_rgba_png(changed)
    return files


def joint_mask(face, connections):
    return sum(1 << i for i, direction in enumerate(FACE_EDGES[face])
               if direction in connections)


def core_model(connections, namespace='howl_sorting_pipes'):
    cap = f'{namespace}:block/pipe_center'
    textures = {'particle': cap, 'cap': cap}
    faces = {}
    for face in DIRECTIONS:
        mask = joint_mask(face, connections)
        key = f'face_{face}'
        textures[key] = f'{namespace}:block/pipe_center_joint_{mask:02x}' if mask else cap
        faces[face] = {'texture': '#' + key}
    return {'textures': textures,
            'elements': [{'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': faces}]}


def arm_model(axis, lo, hi, namespace='howl_sorting_pipes'):
    """Side skins do not repeat end-cap borders along the extension."""
    caps = {'x': ('east', 'west'), 'y': ('up', 'down'), 'z': ('north', 'south')}[axis]
    horizontal = {'x': ('north', 'south', 'up', 'down'), 'y': (),
                  'z': ('east', 'west')}[axis]
    textures = {
        'end': f'{namespace}:block/pipe_center',
        'horizontal': f'{namespace}:block/pipe_arm_side_h',
        'vertical': f'{namespace}:block/pipe_arm_side_v',
        'particle': f'{namespace}:block/pipe_center',
    }
    faces = {face: {'texture': '#end' if face in caps else
                              '#horizontal' if face in horizontal else '#vertical'}
             for face in DIRECTIONS}
    return {'textures': textures,
            'elements': [{'from': lo, 'to': hi, 'faces': faces}]}
