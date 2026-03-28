package fr.bloup.blurpbot.pathfinding;

/**
 * Nœud de recherche A* partagé entre le pathfinding synchrone (monde) et le snapshot async.
 */
public final class PathSearchNode {
    public final int x;
    public final int y;
    public final int z;
    public double g;
    public double h;
    public double f;
    public PathSearchNode parent;

    public PathSearchNode(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long key() {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }
}
