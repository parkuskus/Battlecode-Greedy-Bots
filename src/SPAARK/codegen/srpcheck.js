const allowed = ['0,4', '4,0', '0,-4', '-4,0']
let s = '';
for (let dy = -4; dy <= 4; dy++) {
    for (let dx = -4; dx <= 4; dx++) {
        // allowed locations = out of vision + 4 spots aligned perfectly
        // everything else not allowed
        // don't check center for marker
        if (dx ** 2 + dy ** 2 <= 20 && !allowed.includes(dx + ',' + dy)) {
            s += `\n || G.rc.canSenseLocation(center.translate(${dx}, ${dy}))\n&& (`;
            const a = `[${dy == 0 ? 'oy' : (dy + ' + oy')}][${dx == 0 ? 'ox' : (dx + ' + ox')}]`;
            if (Math.abs(dx) <= 2 && Math.abs(dy) <= 2) {
                s += `!mapInfos${a}.isPassable()`;
                if (dx != 0 || dy != 0)
                    s += ' || ';
            }
            if (dx != 0 || dy != 0)
                s += `mapInfos${a}.getMark() == PaintType.ALLY_SECONDARY)`;
            else
            s += ')';
        }
    }
}
console.log('return' + s.substring(4) + ';');