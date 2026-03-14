var s = "";
for (var i = 20; i <= 60; i++) {
    s += "\npublic static void bfs" + i + "() {"
    for (var j = 1; j <= i; j++) {
        s += "\n    Motion.bfsCurr[" + j + "] = Motion.bfsCurr[" + j + "] | (Motion.bfsCurr[" + j + "] >> 1) | (Motion.bfsCurr[" + j + "] << 1);";
    }
    for (var j = 1; j <= i; j++) {
        s += "\n    Motion.bfsDist[Motion.stepOffset + " + j + "] = (Motion.bfsCurr[" + j + "] | Motion.bfsCurr[" + (j - 1) + "] | Motion.bfsCurr[" + (j + 1) + "]) & (Motion.bitmask ^ Motion.bfsMap[" + j + "]);";
    }
    for (var j = 1; j <= i; j++) {
        s += "\n    Motion.bfsCurr[" + j + "] = Motion.bfsDist[Motion.stepOffset + " + j + "];";
    }
    s += "\n}";
}
console.log(s);