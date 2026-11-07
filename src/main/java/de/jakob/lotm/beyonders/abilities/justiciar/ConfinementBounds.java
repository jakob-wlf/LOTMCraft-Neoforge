package de.jakob.lotm.beyonders.abilities.justiciar;

/** Bounds of the actual block shell, including all corners and negative block coordinates. */
public record ConfinementBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public static ConfinementBounds around(double x, double y, double z, int radius) {
        double bx = Math.floor(x), by = Math.floor(y), bz = Math.floor(z);
        return new ConfinementBounds(bx-radius,by-radius,bz-radius,bx+radius+1,by+radius+1,bz+radius+1);
    }
    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }
    public boolean blocks(boolean sourceWorld, double sx, double sy, double sz,
                          boolean destinationWorld, double dx, double dy, double dz) {
        return (sourceWorld && contains(sx,sy,sz)) != (destinationWorld && contains(dx,dy,dz));
    }
}
