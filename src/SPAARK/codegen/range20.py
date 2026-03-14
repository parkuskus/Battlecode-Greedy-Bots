# worksX,worksY = [],[]
works = []
for dx in range(-5,6):
    for dy in range(-5,6):
        if dx*dx+dy*dy<=20:
            works += [(dx,dy)]
            # worksX+=[dx]
            # worksY+=[dy]
works.sort(key=lambda a:a[0]*a[0]+a[1]*a[1])
if __name__=='__main__':
    print(f'\t\t\tswitch (dx*1000+dy)' + ' {\n')
    for i in works:
        print(f'\t\t\t\tcase {i[0]*10+i[1]} -> G.nearbyMapInfos[{works.index(i)}] = infos[i];')
    print('\t\t\t}')
    # print(list(zip(*works)))