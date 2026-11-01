package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import net.minecraft.util.RandomSource;

public final class NpcNames {
    private NpcNames() {}
    private static final String[] FIRST = {"Alden","Brannoc","Cedric","Dorian","Edric","Fenna","Garrick","Helene", "Isolde","Jory","Kestrel","Lorna","Maren","Nolan","Odric","Perrin","Quill","Rowan","Sabine","Tobias", "Ulric","Vesna","Wren","Yorick","Zella"};
    private static final String[] LAST = {"Ashdown","Blackwell","Cray","Dunmore","Everhart","Fairwind","Greywater", "Hollis","Ironside","Kettering","Marlow","Northcott","Oakes","Pryce","Ravenscroft","Thorne","Whitlock"};
    public static String random(RandomSource r) {
        return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
    }
}