package squidpony.squidgrid.mapping;

import squidpony.annotation.Beta;
import squidpony.squidgrid.Direction;
import squidpony.squidmath.*;

/**
 * Can be used to generate world maps with a wide variety of data, starting with height, temperature and moisture.
 * From there, you can determine biome information in as much detail as your game needs, with a default implementation
 * available that considers any one point as on a gradient between two kinds of biome. The maps this produces are valid
 * for spherical or toroidal world projections, and will wrap from edge to opposite edge seamlessly thanks to a
 * technique from the Accidental Noise Library ( https://www.gamedev.net/blog/33/entry-2138456-seamless-noise/ ) that
 * involves getting a 2D slice of 4D Simplex noise. Because of how Simplex noise works, this also allows extremely high
 * zoom levels as long as certain parameters are within reason. The main trade-off this makes to obtain better quality
 * is reduced speed; generating a 512x512 map on a circa-2016 laptop (i7-6700HQ processor at 2.6 GHz) takes about 1
 * second (about 1.15 seconds for an un-zoomed map, 0.95 or so seconds to increase zoom at double resolution). If you
 * don't need a 512x512 map, this takes commensurately less time to generate less grid cells, with 64x64 maps generating
 * faster than they can be accurately seen on the same hardware. River positions are produced using a different method,
 * and do not involve the Simplex noise parts other than using the height map to determine flow. Zooming with rivers
 * is tricky, and generally requires starting from the outermost zoom level and progressively enlarging and adding
 * detail to all rivers as zoom increases on specified points. Certain parts of this class are not necessary to
 * generate, just in case you want river-less maps or something similar; setting {@link #generateRivers} to false will
 * disable river generation (it defaults to true).
 */
@Beta
public class WorldMapGenerator {
    public final int width, height;
    private static final double terrainFreq = 1.75, terrainRidgedFreq = 1.1, heatFreq = 5.05, moistureFreq = 5.2, otherFreq = 5.5;
    private final Noise.Noise4D terrain, terrainRidged, heat, moisture, otherRidged;
    private long seed, cachedState;
    public StatefulRNG rng;
    public boolean generateRivers = true;
    public final double[][] heightData, heatData, moistureData;
    public final GreasedRegion riverData, lakeData,
            partialRiverData, partialLakeData;
    private final GreasedRegion workingData;
    private final int[][] heightCodeData;
//            heatCodeData,
//            moistureCodeData,
//            biomeUpperCodeData,
//            biomeLowerCodeData;
    public double waterModifier = 0.0, coolingModifier = 1.0,
            minHeight = Double.POSITIVE_INFINITY, maxHeight = Double.NEGATIVE_INFINITY,
            minHeightActual = Double.POSITIVE_INFINITY, maxHeightActual = Double.NEGATIVE_INFINITY,
            minHeat = Double.POSITIVE_INFINITY, maxHeat = Double.NEGATIVE_INFINITY,
            minWet = Double.POSITIVE_INFINITY, maxWet = Double.NEGATIVE_INFINITY;
    private double minHeat0 = Double.POSITIVE_INFINITY, maxHeat0 = Double.NEGATIVE_INFINITY,
            minHeat1 = Double.POSITIVE_INFINITY, maxHeat1 = Double.NEGATIVE_INFINITY,
            minWet0 = Double.POSITIVE_INFINITY, maxWet0 = Double.NEGATIVE_INFINITY;
    private int zoom = 0;
    private IntVLA startCacheX = new IntVLA(8), startCacheY = new IntVLA(8);
    public static final double
            deepWaterLower = -1.0, deepWaterUpper = -0.7,        // 0
            mediumWaterLower = -0.7, mediumWaterUpper = -0.3,    // 1
            shallowWaterLower = -0.3, shallowWaterUpper = -0.1,  // 2
            coastalWaterLower = -0.1, coastalWaterUpper = 0.1,   // 3
            sandLower = 0.1, sandUpper = 0.18,                   // 4
            grassLower = 0.18, grassUpper = 0.35,                // 5
            forestLower = 0.35, forestUpper = 0.6,               // 6
            rockLower = 0.6, rockUpper = 0.8,                    // 7
            snowLower = 0.8, snowUpper = 1.0;                    // 8

    public static final double[] lowers = {deepWaterLower, mediumWaterLower, shallowWaterLower, coastalWaterLower,
            sandLower, grassLower, forestLower, rockLower, snowLower};
    public WorldMapGenerator()
    {
        this(0x1337BABE1337D00DL, 256, 256, SeededNoise.instance);
    }
    public WorldMapGenerator(int mapWidth, int mapHeight)
    {
        this(0x1337BABE1337D00DL, mapWidth, mapHeight, SeededNoise.instance);
    }
    public WorldMapGenerator(long initialSeed, int mapWidth, int mapHeight)
    {
        this(initialSeed, mapWidth, mapHeight, SeededNoise.instance);
    }
    public WorldMapGenerator(long initialSeed, int mapWidth, int mapHeight, Noise.Noise4D noiseGenerator)
    {
        width = mapWidth;
        height = mapHeight;
        seed = initialSeed;
        cachedState = ~initialSeed;
        rng = new StatefulRNG(initialSeed);
        heightData = new double[width][height];
        heatData = new double[width][height];
        moistureData = new double[width][height];
        riverData = new GreasedRegion(width, height);
        lakeData = new GreasedRegion(width, height);
        partialRiverData = new GreasedRegion(width, height);
        partialLakeData = new GreasedRegion(width, height);
        workingData = new GreasedRegion(width, height);
        heightCodeData = new int[width][height];
        /*
        heatCodeData = new int[width][height];
        moistureCodeData = new int[width][height];

        biomeUpperCodeData = new int[width][height];
        biomeLowerCodeData = new int[width][height];
        */
        terrain = new Noise.Layered4D(noiseGenerator, 8, terrainFreq);
        terrainRidged = new Noise.Ridged4D(noiseGenerator, 10, terrainRidgedFreq);
        heat = new Noise.Layered4D(noiseGenerator, 3, heatFreq);
        moisture = new Noise.Layered4D(noiseGenerator, 4, moistureFreq);
        otherRidged = new Noise.Ridged4D(noiseGenerator, 6, otherFreq);

    }

    public void generate()
    {
        generate(rng.nextLong());
    }

    public void generate(long state)
    {
        if(cachedState != state)
        {
            seed = state;
            zoom = 0;
            startCacheX.clear();
            startCacheY.clear();
            startCacheX.add(0);
            startCacheY.add(0);

        }
        regenerate(startCacheX.peek(), startCacheY.peek(),
                (width >> zoom), (height >> zoom), state);
    }

    public void zoomOut()
    {
        zoomOut(width >> 1, height >> 1);
    }
    public void zoomOut(int zoomCenterX, int zoomCenterY)
    {
        if(zoom > 0)
        {
            if(seed != cachedState)
            {
                generate(rng.nextLong());
            }

            zoom--;
            startCacheX.pop();
            startCacheY.pop();
            startCacheX.add(Math.min(Math.max(startCacheX.pop() + (zoomCenterX >> zoom + 1) - (width >> zoom + 2),
                    0), width - (width >> zoom)));
            startCacheY.add(Math.min(Math.max(startCacheY.pop() + (zoomCenterY >> zoom + 1) - (height >> zoom + 2),
                    0), height - (height >> zoom)));
            regenerate(startCacheX.peek(), startCacheY.peek(),width >> zoom, height >> zoom, cachedState);
            rng.setState(cachedState);
        }

    }
    public void zoomIn()
    {
        zoomIn(width >> 1, height >> 1);
    }

    public void zoomIn(int zoomCenterX, int zoomCenterY)
    {
        if(seed != cachedState)
        {
            generate(rng.nextLong());
        }
        zoom++;
        if(startCacheX.isEmpty())
        {
            startCacheX.add(0);
            startCacheY.add(0);
        }
        else {
            startCacheX.add(Math.min(Math.max(startCacheX.peek() + (zoomCenterX >> zoom - 1) - (width >> zoom + 1),
                    0), width - (width >> zoom)));
            startCacheY.add(Math.min(Math.max(startCacheY.peek() + (zoomCenterY >> zoom - 1) - (height >> zoom + 1),
                    0), height - (height >> zoom)));
        }
        regenerate(startCacheX.peek(), startCacheY.peek(),width >> zoom, height >> zoom, cachedState);
        rng.setState(cachedState);
    }

    protected void regenerate(int startX, int startY, int usedWidth, int usedHeight, long state)
    {
        //long startTime = System.currentTimeMillis();
        boolean fresh = false;
        if(cachedState != state)
        {
            minHeight = Double.POSITIVE_INFINITY;
            maxHeight = Double.NEGATIVE_INFINITY;
            minHeat0 = Double.POSITIVE_INFINITY;
            maxHeat0 = Double.NEGATIVE_INFINITY;
            minHeat1 = Double.POSITIVE_INFINITY;
            maxHeat1 = Double.NEGATIVE_INFINITY;
            minHeat = Double.POSITIVE_INFINITY;
            maxHeat = Double.NEGATIVE_INFINITY;
            minWet0 = Double.POSITIVE_INFINITY;
            maxWet0 = Double.NEGATIVE_INFINITY;
            minWet = Double.POSITIVE_INFINITY;
            maxWet = Double.NEGATIVE_INFINITY;
            cachedState = state;
            fresh = true;
        }
        rng.setState(state);
        int seedA = rng.nextInt(), seedB = rng.nextInt(), seedC = rng.nextInt(), t;
        waterModifier = rng.nextDouble(0.25) + 0.89;
        coolingModifier = rng.nextDouble(0.45) * (rng.nextDouble()-0.5) + 1.1;

        double p, q,
                ps, pc,
                qs, qc,
                h, temp,
                i_w = 6.283185307179586 / width, i_h = 6.283185307179586 / height,
                xPos = startX, yPos = startY, i_uw = usedWidth / (double)width, i_uh = usedHeight / (double)height;
        double[] trigTable = new double[width << 1];
        for (int x = 0; x < width; x++, xPos += i_uw) {
            p = xPos * i_w;
            trigTable[x<<1]   = Math.sin(p);
            trigTable[x<<1|1] = Math.cos(p);
        }
        for (int y = 0; y < height; y++, yPos += i_uh) {
            q = yPos * i_h;
            qs = Math.sin(q);
            qc = Math.cos(q);
            for (int x = 0, xt = 0; x < width; x++) {
                ps = trigTable[xt++];//Math.sin(p);
                pc = trigTable[xt++];//Math.cos(p);
                h = terrain.getNoiseWithSeed(pc +
                                terrainRidged.getNoiseWithSeed(pc, ps, qc, qs, seedA + seedB),
                        ps, qc, qs, seedA);
                //p = Math.signum(h) + waterModifier;
                //h *= p * p;
                h *= waterModifier;
                heightData[x][y] = h;
                heatData[x][y] = (p = heat.getNoiseWithSeed(pc, ps, qc
                                + otherRidged.getNoiseWithSeed(pc, ps, qc, qs, seedB + seedC)//, seedD + seedC)
                        , qs, seedB));
                moistureData[x][y] = (temp = moisture.getNoiseWithSeed(pc, ps, qc, qs
                                + otherRidged.getNoiseWithSeed(pc, ps, qc, qs, seedC + seedA)//seedD + seedB)
                        , seedC));
                minHeightActual = Math.min(minHeightActual, h);
                maxHeightActual = Math.max(maxHeightActual, h);
                if(fresh) {
                    minHeight = Math.min(minHeight, h);
                    maxHeight = Math.max(maxHeight, h);

                    minHeat0 = Math.min(minHeat0, p);
                    maxHeat0 = Math.max(maxHeat0, p);

                    minWet0 = Math.min(minWet0, temp);
                    maxWet0 = Math.max(maxWet0, temp);

                }
            }
            minHeightActual = Math.min(minHeightActual, minHeight);
            maxHeightActual = Math.max(maxHeightActual, maxHeight);

        }
        double heightDiff = 2.0 / (maxHeightActual - minHeightActual),
                heatDiff = 0.8 / (maxHeat0 - minHeat0),
                wetDiff = 1.0 / (maxWet0 - minWet0),
                hMod,
                halfHeight = (height - 1) * 0.5, i_half = 1.0 / halfHeight;
        double minHeightActual0 = minHeightActual;
        double maxHeightActual0 = maxHeightActual;
        yPos = startY;
        ps = Double.POSITIVE_INFINITY;
        pc = Double.NEGATIVE_INFINITY;

        for (int y = 0; y < height; y++, yPos += i_uh) {
            temp = Math.abs(yPos - halfHeight) * i_half;
            temp *= (2.4 - temp);
            temp = 2.2 - temp;
            for (int x = 0; x < width; x++) {
                heightData[x][y] = (h = (heightData[x][y] - minHeightActual) * heightDiff - 1.0);
                minHeightActual0 = Math.min(minHeightActual0, h);
                maxHeightActual0 = Math.max(maxHeightActual0, h);
                heightCodeData[x][y] = (t = codeHeight(h));
                hMod = 1.0;
                switch (t) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                        h = 0.4;
                        hMod = 0.2;
                        break;
                    case 6:
                        h = -0.1 * (h - forestLower - 0.08);
                        break;
                    case 7:
                        h *= -0.25;
                        break;
                    case 8:
                        h *= -0.4;
                        break;
                    default:
                        h *= 0.05;
                }
                heatData[x][y] = (h = (((heatData[x][y] - minHeat0) * heatDiff * hMod) + h + 0.6) * temp);
                if (fresh) {
                    ps = Math.min(ps, h); //minHeat0
                    pc = Math.max(pc, h); //maxHeat0
                }
            }
        }
        if(fresh)
        {
            minHeat1 = ps;
            maxHeat1 = pc;
        }
        heatDiff = coolingModifier / (maxHeat1 - minHeat1);
        qs = Double.POSITIVE_INFINITY;
        qc = Double.NEGATIVE_INFINITY;
        ps = Double.POSITIVE_INFINITY;
        pc = Double.NEGATIVE_INFINITY;


        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                heatData[x][y] = (h = ((heatData[x][y] - minHeat1) * heatDiff));
                //(h = Math.pow(h, 2.0 - h * 2.0));
                moistureData[x][y] = (temp = (moistureData[x][y] - minWet0) * wetDiff); // may assign to temp?
                if (fresh) {
                    qs = Math.min(qs, h);
                    qc = Math.max(qc, h);
                    ps = Math.min(ps, temp);
                    pc = Math.max(pc, temp);
                }
            }
        }
        if(fresh)
        {
            minHeat = qs;
            maxHeat = qc;
            minWet = ps;
            maxWet = pc;
        }
        if(generateRivers) {
            if (fresh) {
                addRivers();
                riverData.connect8way().thin().thin();
                partialRiverData.remake(riverData);
                partialLakeData.remake(lakeData);
            } else {
                partialRiverData.remake(riverData);
                partialLakeData.remake(lakeData);
                for (int i = 1; i <= zoom; i++) {
                    int stx = (startCacheX.get(i) - startCacheX.get(i - 1)) << (i - 1),
                            sty = (startCacheY.get(i) - startCacheY.get(i - 1)) << (i - 1);
                    if ((i & 3) == 3) {
                        partialRiverData.zoom(stx, sty).connect8way();//.connect8way();//.connect();
                        partialRiverData.or(workingData.remake(partialRiverData).fringe().quasiRandomRegion(0.4));
                        partialLakeData.zoom(stx, sty).connect8way();//.connect8way();//.connect();
                        partialLakeData.or(workingData.remake(partialLakeData).fringe().quasiRandomRegion(0.55));
                    } else {
                        partialRiverData.zoom(stx, sty).connect8way().thin();//.connect8way();//.connect();
                        partialRiverData.or(workingData.remake(partialRiverData).fringe().quasiRandomRegion(0.5));
                        partialLakeData.zoom(stx, sty).connect8way().thin();//.connect8way();//.connect();
                        partialLakeData.or(workingData.remake(partialLakeData).fringe().quasiRandomRegion(0.7));
                    }
                }
            }
        }
        /*
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                codeBiome(x, y, heatData[x][y], moistureData[x][y], heightData[x][y], heightCodeData[x][y]);
            }
        }*/
        //ttg = System.currentTimeMillis() - startTime;
    }
    public int codeHeight(final double high)
    {
        if(high < deepWaterUpper)
            return 0;
        if(high < mediumWaterUpper)
            return 1;
        if(high < shallowWaterUpper)
            return 2;
        if(high < coastalWaterUpper)
            return 3;
        if(high < sandUpper)
            return 4;
        if(high < grassUpper)
            return 5;
        if(high < forestUpper)
            return 6;
        if(high < rockUpper)
            return 7;
        return 8;
    }
    protected final int decodeX(final int coded)
    {
        return coded % width;
    }
    protected final int decodeY(final int coded)
    {
        return coded / width;
    }
    private static final Direction[] reuse = new Direction[9];
    private void appendDirToShuffle(RNG rng) {
        rng.randomPortion(Direction.CARDINALS, reuse);
        reuse[rng.next(2)] = Direction.DIAGONALS[rng.next(2)];
        reuse[4] = Direction.DIAGONALS[rng.next(2)];
        reuse[5] = Direction.OUTWARDS[rng.next(3)];
    }

    protected void addRivers()
    {
        long rebuildState = rng.nextLong();
        workingData.empty().insertRectangle(8, 8, width - 16, height - 16);
        riverData.empty().refill(heightCodeData, 6, 100);
        riverData.quasiRandomRegion(0.000003 * Math.max(width, height)).and(workingData);
        int[] starts = riverData.asTightEncoded();
        int len = starts.length, currentPos, choice, adjX, adjY, currX, currY, tcx, tcy, stx, sty, sbx, sby;
        riverData.clear();
        lakeData.clear();
        PER_RIVER:
        for (int i = 0; i < len; i++) {
            workingData.clear();
            currentPos = starts[i];
            stx = tcx = currX = decodeX(currentPos);
            sty = tcy = currY = decodeY(currentPos);
            while (true) {

                double best = 999999;
                choice = -1;
                appendDirToShuffle(rng);

                for (int d = 0; d < 5; d++) {
                    adjX = (currX + reuse[d].deltaX);
                    if (adjX < 0 || adjX >= width)
                    {
                        if(rng.next(4) == 0)
                            riverData.or(workingData);
                        continue PER_RIVER;
                    }
                    adjY = (currY + reuse[d].deltaY);
                    if (adjY < 0 || adjY >= height)
                    {
                        if(rng.next(4) == 0)
                            riverData.or(workingData);
                        continue PER_RIVER;
                    }
                    if (heightData[adjX][adjY] < best && !workingData.contains(adjX, adjY)) {
                        best = heightData[adjX][adjY];
                        choice = d;
                        tcx = adjX;
                        tcy = adjY;
                    }
                }
                currX = tcx;
                currY = tcy;
                if (best >= heightData[stx][sty]) {
                    tcx = rng.next(2);
                    adjX = currX + ((tcx & 1) << 1) - 1;
                    adjY = currY + (tcx & 2) - 1;
                    lakeData.insert(currX, currY);
                    lakeData.insert(currX + 1, currY);
                    lakeData.insert(currX - 1, currY);
                    lakeData.insert(currX, currY + 1);
                    lakeData.insert(currX, currY - 1);
                    if (!(adjY < 0 || adjY >= height || adjX < 0 || adjX >= width))
                    {
                        if(heightCodeData[adjX][adjY] <= 3) {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }
                        else if((heightData[adjX][adjY] -= 0.0002) < lowers[heightCodeData[adjX][adjY]-1])
                        {
                            if(rng.next(3) == 0)
                                riverData.or(workingData);
                            continue PER_RIVER;
                        }
                    }
                    else
                    {
                        if(rng.next(5) == 0)
                            riverData.or(workingData);
                        continue PER_RIVER;
                    }
                    tcx = rng.next(2);
                    adjX = currX + ((tcx & 1) << 1) - 1;
                    adjY = currY + (tcx & 2) - 1;
                    if (!(adjY < 0 || adjY >= height || adjX < 0 || adjX >= width))
                    {
                        if(heightCodeData[adjX][adjY] <= 3) {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }
                        else if((heightData[adjX][adjY] -= 0.0002) < lowers[heightCodeData[adjX][adjY]-1])
                        {
                            if(rng.next(3) == 0)
                                riverData.or(workingData);
                            continue PER_RIVER;
                        }
                    }
                    else
                    {
                        if(rng.next(5) == 0)
                            riverData.or(workingData);
                        continue PER_RIVER;
                    }

                }
                if(choice != -1 && reuse[choice].isDiagonal())
                {
                    tcx = currX - reuse[choice].deltaX;
                    tcy = currY - reuse[choice].deltaY;
                    if(heightData[tcx][currY] <= heightData[currX][tcy] && !workingData.contains(tcx, currY))
                    {
                        if(riverData.contains(tcx, currY))
                        {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }
                        workingData.insert(tcx, currY);
                        if (heightData[tcx][currY] <= 0.075)
                        {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }
                    }
                    else if(!workingData.contains(currX, tcy))
                    {
                        if(riverData.contains(currX, tcy))
                        {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }
                        workingData.insert(currX, tcy);
                        if (heightData[currX][tcy] <= 0.075)
                        {
                            riverData.or(workingData);
                            continue PER_RIVER;
                        }

                    }
                }
                if(riverData.contains(currX, currY))
                {
                    riverData.or(workingData);
                    continue PER_RIVER;
                }
                workingData.insert(currX, currY);
                if (heightData[currX][currY] <= 0.075)
                {
                    riverData.or(workingData);
                    continue PER_RIVER;
                }
            }
        }

        GreasedRegion tempData = new GreasedRegion(width, height);
        int riverCount = riverData.size() >> 3, currentMax = riverCount >> 2, idx = 0, prevChoice;
        for (int h = 5; h < 9; h++) { //, currentMax += riverCount / 18
            workingData.empty().refill(heightCodeData, h).and(riverData);
            RIVER:
            for (int j = 0; j < currentMax && idx < riverCount; j++) {
                double vdc = VanDerCorputQRNG.weakDetermine(idx++), best = -999999;
                currentPos = workingData.atFractionTight(vdc);
                if(currentPos < 0)
                    break;
                stx = sbx = tcx = currX = decodeX(currentPos);
                sty = sby = tcy = currY = decodeY(currentPos);
                appendDirToShuffle(rng);
                choice = -1;
                prevChoice = -1;
                for (int d = 0; d < 5; d++) {
                    adjX = (currX + reuse[d].deltaX);
                    if (adjX < 0 || adjX >= width)
                        continue;
                    adjY = (currY + reuse[d].deltaY);
                    if (adjY < 0 || adjY >= height)
                        continue;
                    if (heightData[adjX][adjY] > best) {
                        best = heightData[adjX][adjY];
                        prevChoice = choice;
                        choice = d;
                        sbx = tcx;
                        sby = tcy;
                        tcx = adjX;
                        tcy = adjY;
                    }
                }
                currX = sbx;
                currY = sby;
                if (prevChoice != -1 && heightCodeData[currX][currY] >= 4) {
                    if (reuse[prevChoice].isDiagonal()) {
                        tcx = currX - reuse[prevChoice].deltaX;
                        tcy = currY - reuse[prevChoice].deltaY;
                        if (heightData[tcx][currY] <= heightData[currX][tcy])
                            tempData.insert(tcx, currY);
                        else
                            tempData.insert(currX, tcy);
                    }
                    tempData.insert(currX, currY);
                }

                while (true) {
                    best = -999999;
                    appendDirToShuffle(rng);
                    choice = -1;
                    for (int d = 0; d < 6; d++) {
                        adjX = (currX + reuse[d].deltaX);
                        if (adjX < 0 || adjX >= width)
                            continue;
                        adjY = (currY + reuse[d].deltaY);
                        if (adjY < 0 || adjY >= height)
                            continue;
                        if (heightData[adjX][adjY] > best && !riverData.contains(adjX, adjY)) {
                            best = heightData[adjX][adjY];
                            choice = d;
                            sbx = adjX;
                            sby = adjY;
                        }
                    }
                    currX = sbx;
                    currY = sby;
                    if (choice != -1 && heightCodeData[currX][currY] >= 4) {
                        if (reuse[choice].isDiagonal()) {
                            tcx = currX - reuse[choice].deltaX;
                            tcy = currY - reuse[choice].deltaY;
                            if (heightData[tcx][currY] <= heightData[currX][tcy])
                                tempData.insert(tcx, currY);
                            else
                                tempData.insert(currX, tcy);
                        }
                        tempData.insert(currX, currY);
                    }
                    if (best <= heightData[stx][sty] || heightData[currX][currY] > rng.nextDouble(280.0)) {
                        riverData.or(tempData);
                        tempData.clear();

                        lakeData.insert(currX, currY);
                        sbx = rng.next(8);
                        sbx &= sbx >>> 4;
                        if ((sbx & 1) == 0)
                            lakeData.insert(currX + 1, currY);
                        if ((sbx & 2) == 0)
                            lakeData.insert(currX - 1, currY);
                        if ((sbx & 4) == 0)
                            lakeData.insert(currX, currY + 1);
                        if ((sbx & 8) == 0)
                            lakeData.insert(currX, currY - 1);
                        sbx = rng.next(2);
                        lakeData.insert(currX + (-(sbx & 1) | 1), currY + ((sbx & 2) - 1)); // random diagonal
                        lakeData.insert(currX, currY + ((sbx & 2) - 1)); // ortho next to random diagonal
                        lakeData.insert(currX + (-(sbx & 1) | 1), currY); // ortho next to random diagonal

                        continue RIVER;
                    }
                }
            }

        }

        rng.setState(rebuildState);
    }

}
