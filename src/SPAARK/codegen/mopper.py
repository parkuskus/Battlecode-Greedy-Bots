from range20 import works
import sys


#transfer scores
a = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
for i in range(1,25):
    print("""\t\tloc = G.me.translate("""+str(works[i][0])+""", """+str(works[i][1])+""");
        if (G.rc.onTheMap(loc) && G.rc.canSenseRobotAtLocation(loc)) {
            RobotInfo r = G.rc.senseRobotAtLocation(loc);
            if (r.team == G.team && G.rc.getPaint() > 70 && r.type != UnitType.MOPPER) {
                transferScores["""+str(i)+"""] += Math.min(G.rc.getPaint() - 50, r.type.paintCapacity - r.paintAmount) * 3;
                if (r.paintAmount == 0) {
                    transferScores["""+str(i)+"""] += 101;
                }
            }
        }""")

#mop
#weighing scores
a = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
for d in a:
    asdf = []
    s = ''
    for dx in range(-1, 2):
        for dy in range(-1, 2):
            if (dx+d[0])**2 + (dy+d[1])**2 > 2:
                ind = works.index((dx+d[0],dy+d[1]))
                # s += f'\t\t//if we move to [{d[0]}, {d[1]}] (index {a.index(d)}), then we will be able to attack [{dx+d[0]}, {dy+d[1]}] (index {ind}), so check that too\n'
                s += f'\t\tif (transferScores[{ind}] > allmax[{a.index(d)}])' + ' {\n'
                s += f'\t\t\tallmax[{a.index(d)}] = transferScores[{ind}];\n'
                s += f'\t\t\tallx[{a.index(d)}] = {dx+d[0]};\n'
                s += f'\t\t\tally[{a.index(d)}] = {dy+d[1]};\n'
                s += f'\t\t\talltype[{a.index(d)}] = TRANSFER;\n'
                s += '\t\t}\n'
                # asdf += [works.index((dx+d[0],dy+d[1]))]
    # print(d,asdf)
    print(s,end='')
exit()

#calculating scores
for i in range(25):
    print("""\t\tloc = G.me.translate("""+str(works[i][0])+""", """+str(works[i][1])+""");
        if (G.rc.onTheMap(loc)) {
            PaintType paint = G.rc.senseMapInfo(loc).getPaint();
            if (paint.isEnemy()) {
                attackScores["""+str(i)+"""] += 25;
            }
            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo bot = G.rc.senseRobotAtLocation(loc);
                if (bot.team == G.opponentTeam) {
                    attackScores["""+str(i)+"""] += (Math.min(10, bot.paintAmount) + Math.min(5, UnitType.MOPPER.paintCapacity - G.rc.getPaint())) * 5;
                    if (bot.paintAmount > 0) {
                        if (bot.paintAmount <= 10) {
                            attackScores["""+str(i)+"""] += (int) G.paintPerChips() * bot.type.moneyCost;
                        } else {
                            attackScores["""+str(i)+"""] += (G.cooldown(bot.paintAmount - 10, 10, bot.type.paintCapacity) - G.cooldown(bot.paintAmount, 10, bot.type.paintCapacity)) * G.paintPerCooldown();
                        }
                    }
                }
            }
        }""")

            # if (target.distanceSquaredTo(loc) <= 8) {
            #     attackScores["""+str(i)+"""] += MOP_TOWER_WEIGHT;
            # }
print()
print()
print()
#swings
#calculating scores
a = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (0, 0)]
a2 = [
    [(0, -1), (0, -2), (-1, -1), (-1, -2), (1, -1), (1, -2)],
    [(-1, 0), (-2, 0), (-1, -1), (-2, -1), (-1, 1), (-2, 1)],
    [(1, 0), (2, 0), (1, 1), (2, 1), (1, -1), (2, -1)],
    [(0, 1), (0, 2), (1, 1), (1, 2), (-1, 1), (-1, 2)]
]
s = ['\t\t\tMapLocation loc;\n']
for d in a:
    for d2 in a2:
        for i in d2:
            try:
                ind = s.index(f'\t\tloc = G.me.translate({d[0]+i[0]}, {d[1]+i[1]});\n')
                if ind < 0:
                    raise Exception()
                s.insert(s.index(f'\t\t\t\tif (bot.paintAmount <= 5){{\n',ind)+2, f'\t\t\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd;\n')
                s.insert(s.index(f'\t\t\t\t}} else {{\n',ind)+2, f'\t\t\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd;\n')
                s.insert(ind+4, f'\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd2;\n')
            except:
                s.append(f'\t\tloc = G.me.translate({d[0]+i[0]}, {d[1]+i[1]});\n')
                s.append(f'\t\tif (G.opponentRobotsString.indexOf(loc.toString()) != -1)' + ' {\n')
                s.append(f'\t\t\tRobotInfo bot = G.rc.senseRobotAtLocation(loc);\n')
                s.append(f'\t\t\tint toAdd2 = Math.min(5, bot.paintAmount) * 5;\n')
                s.append(f'\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd2;\n')
                s.append(f'\t\t\tif (bot.paintAmount > 0) {{\n')
                s.append(f'\t\t\t\tif (bot.paintAmount <= 5){{\n')
                s.append(f'\t\t\t\t\tint toAdd = (int) (G.paintPerChips() * bot.type.moneyCost);\n')
                s.append(f'\t\t\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd;\n')
                s.append(f'\t\t\t\t}} else {{\n')
                s.append(f'\t\t\t\t\tint toAdd = (int) ((G.cooldown(bot.paintAmount - 5, 10, bot.type.paintCapacity) - G.cooldown(bot.paintAmount, 10, bot.type.paintCapacity)) * G.paintPerCooldown());\n')
                s.append(f'\t\t\t\t\tswingScores[{a.index(d)*4+a2.index(d2)}] += toAdd;\n')
                s.append(f'\t\t\t\t}}\n')
                s.append('\t\t\t}\n')
                s.append('\t\t}\n')
s+= [f'\t\tswingScores[{i}] *= MOP_SWING_MULT;\n' for i in range(36)]
# s = s.split('\n')
print(''.join(s))

#weighing scores
s=''
for d2 in a2:
    s += f'\t\tif (swingScores[{32+a2.index(d2)}] > cmax)' + ' {\n'
    s += f'\t\t\tcmax = swingScores[{32+a2.index(d2)}];\n'
    s += f'\t\t\tcx = {a.index(d2[0])};\n'
    s += '\t\t\tswing = true;\n'
    s += '\t\t}\n'
print(s)
s=''
for d in a[:-1]:
    for d2 in a2:
        s += f'\t\tif (swingScores[{a.index(d)*4+a2.index(d2)}] > allmax[{a.index(d)}])' + ' {\n'
        s += f'\t\t\tallmax[{a.index(d)}] = swingScores[{a.index(d)*4+a2.index(d2)}];\n'
        s += f'\t\t\tallx[{a.index(d)}] = {a.index(d2[0])};\n'
        s += f'\t\t\tallswing[{a.index(d)}] = true;\n'
        s += '\t\t}\n'
print(s)