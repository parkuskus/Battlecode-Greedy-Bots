from range20 import works
# import pyperclip
# pyperclip.copy(''.join(['attackScores['+str(i)+'] = ' for i in range(37)]) + ''.join(['attackScores2['+str(i)+'] = ' for i in range(69)]) + ''.join(['moveScores['+str(i)+'] = ' for i in range(9)]) + '0;')

#lol
a = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (0, 0)]
s = ''
for i in range(69):
    s += f'\t\tloc = G.me.translate{works[i]};\n'
    s += '\t\tif (G.rc.onTheMap(loc))' + ' {\n'
    s += '\t\t\tMapInfo info = G.rc.senseMapInfo(loc);\n'
    s += '\t\t\tif (!info.isWall()) {\n'
    s += '\t\t\t\tif (info.getPaint() == PaintType.EMPTY) {\n'
    s += '\t\t\t\t\tif (info.hasRuin()) {\n'
    s += '\t\t\t\t\t\tif (G.rc.canSenseRobotAtLocation(loc) && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {\n'
    for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\t\tattackScores[{ind}] += G.paintPerChips() * 100;\n'
        except:
            pass
    s += '\t\t\t\t\t\t} else {\n'
    for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\t\tattackScores[{ind}] += 100;\n'
        except:
            pass
    s += '\t\t\t\t\t\t}\n'
    s += '\t\t\t\t\t} else {\n'
    for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\tattackScores[{ind}] += 25;\n'
        except:
            pass
    s += '\t\t\t\t\t}\n'
    s += '\t\t\t\t\tif (G.opponentRobotsString.indexOf(loc.toString()) != -1) {\n'
    for j in a[:1]:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\tattackScores[{ind}] += 25;\n'
        except:
            pass
    s += '\t\t\t\t\t} else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {\n'
    for j in a[:1]:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\tattackScores[{ind}] += 25;\n'
        except:
            pass
    s += '\t\t\t\t\t}\n'
    s += '\t\t\t\t} else if (info.getPaint().isEnemy()) {\n'
    for j in a:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\tattackScores[{ind}] += 50;\n'
        except:
            pass
    s += '\t\t\t\t\tif (G.opponentRobotsString.indexOf(loc.toString()) != -1) {\n'
    for j in a[:1]:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\tattackScores[{ind}] += 50;\n'
        except:
            pass
    s += '\t\t\t\t\t} else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {\n'
    for j in a[:1]:
        try:
            ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
            if ind < 37:
                s += f'\t\t\t\t\t\tattackScores[{ind}] += 50;\n'
        except:
            pass
    s += '\t\t\t\t\t}\n'
    s += '\t\t\t\t}\n'
    s += '\t\t\t}\n'
    s += '\t\t}\n'

    # s += '\t\t\tPaintType paint = G.rc.senseMapInfo(loc).getPaint();\n'
    # s += '\t\t\tif (paint == PaintType.EMPTY) {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\tattackScores[{ind}] += 25;\n'
    #     except:
    #         pass
    # s += '\t\t\t\tif (G.opponentRobotsString.indexOf(loc.toString()) != -1) {\n'
    # s += '\t\t\t\t\tif (ruinsString.indexOf(loc.toString()) == -1) {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\t\t\tattackScores[{ind}] += 25;\n'
    #     except:
    #         pass
    # s += '\t\t\t\t\t} else {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\t\t\tattackScores[{ind}] += G.paintPerChips() * 100;\n'
    #     except:
    #         pass
    # s += '\t\t\t\t\t}\n'
    # s += '\t\t\t\t} else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\t\tattackScores[{ind}] += 25;\n'
    #     except:
    #         pass
    # s += '\t\t\t\t}\n'
    # s += '\t\t\t}\n'
    # s += '\t\t\telse if (paint.isEnemy()) {\n'
    # for j in a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\tattackScores[{ind}] += 50;\n'
    #     except:
    #         pass
    # s += '\t\t\t\tif (G.opponentRobotsString.indexOf(loc.toString()) != -1) {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\t\tattackScores[{ind}] += 50;\n'
    #     except:
    #         pass
    # s += '\t\t\t\t} else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {\n'
    # for j in [(2, 0), (0, 2), (-2, 0), (0, -2)]+a:
    #     try:
    #         ind = works.index((j[0]+works[i][0], j[1]+works[i][1]))
    #         if ind < 37:
    #             s += f'\t\t\t\t\tattackScores[{ind}] += 50;\n'
    #     except:
    #         pass
    # s += '\t\t\t\t}\n'
    # s += '\t\t\t}\n'
    # s += '\t\t}\n'
print(s)

#find best attackScores
# a = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
# s = ''
# for d in a:
#     for d2 in works:
#         if d2[0]**2+d2[1]**2<=4 and (d[0]+d2[0])**2 + (d[1]+d2[1])**2 > 4:
#             ind = works.index((d2[0]+d[0],d2[1]+d[1]))
#             s += f'\t\tif (attackScores[{ind}] > allmax[{a.index(d)}])' + ' {\n'
#             s += f'\t\t\tallmax[{a.index(d)}] = attackScores[{ind}];\n'
#             s += f'\t\t\tallx[{a.index(d)}] = {d2[0]+d[0]};\n'
#             s += f'\t\t\tally[{a.index(d)}] = {d2[1]+d[1]};\n'
#             s += '\t\t}\n'
# print(s)