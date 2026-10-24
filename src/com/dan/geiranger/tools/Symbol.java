package com.dan.geiranger.tools;

import com.dan.geiranger.ui.AppIcon;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 18 (F23 A): das Programmsymbol als Dateien. Malt {@link AppIcon} in allen Größen und schreibt
 * {@code src/com/dan/geiranger/ui/icon/geiranger_NN.png} (ins Jar, für Fenster und Taskleiste) und {@code Geiranger.ico}
 * (alle Größen als PNG im ICO, für die Verknüpfung von {@code Geiranger_Verknuepfung.bat}). Aufruf ohne Argumente aus
 * dem Projektordner (Ant-Target {@code symbol}).
 */
public final class Symbol {
    private Symbol() { }

    /** Ein ICO mit PNG-Einträgen (Windows Vista und neuer). */
    public static byte[] ico(List<byte[]> pngs, int[] groessen) {
        int n = pngs.size(), off = 6 + 16 * n, len = off;
        for (byte[] p : pngs) len += p.length;
        ByteBuffer b = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) n);
        for (int i = 0; i < n; i++) {
            int s = groessen[i];
            b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
            off += pngs.get(i).length;
        }
        for (byte[] p : pngs) b.put(p);
        return b.array();
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "src/com/dan/geiranger/ui/icon"), ico = new File(a.length > 1 ? a[1] : "Geiranger.ico");
        dir.mkdirs();
        List<byte[]> pngs = new ArrayList<>();
        for (int s : AppIcon.SIZES) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ImageIO.write(AppIcon.paint(s), "png", o);
            pngs.add(o.toByteArray());
            Files.write(new File(dir, "geiranger_" + s + ".png").toPath(), o.toByteArray());
        }
        Files.write(ico.toPath(), ico(pngs, AppIcon.SIZES));
        System.out.println("Symbol: " + AppIcon.SIZES.length + " PNG in " + dir + ", " + ico + " (" + ico.length() + " Bytes)");
    }
}
