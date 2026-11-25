package com.onefilm.android.tv.player.media;

import androidx.media3.common.MediaChapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal EBML parser for Matroska {@code Chapters}.
 *
 * <p>Reads the top-level elements until {@code Chapters} is found, then extracts edition/chapter
 * atoms (time start + display title). Returns an empty list for non-Matroska input.
 */
public final class MkvChapterParser {

    private static final long ID_CHAPTERS = 0x1043A770L;
    private static final long ID_SEGMENT = 0x18538067L;
    private static final long ID_EDITION = 0x45B9L;
    private static final long ID_ATOM = 0xB6L;
    private static final long ID_TIME_START = 0x91L;
    private static final long ID_DISPLAY = 0x80L;
    private static final long ID_STRING = 0x85L;

    private MkvChapterParser() {
    }

    public static List<MediaChapter> parse(byte[] data) {
        if (data == null || data.length < 4) return Collections.emptyList();
        int[] pos = {0};
        while (pos[0] < data.length) {
            int elementStart = pos[0];
            long id = readId(data, pos);
            if (id < 0) break;
            long size = readSize(data, pos);
            if (size < 0) size = data.length - pos[0];
            int bodyStart = pos[0];
            if (bodyStart + size > data.length) break;
            if (id == ID_CHAPTERS) {
                List<MediaChapter> chapters = new ArrayList<>();
                parseChapters(data, bodyStart, (int) (bodyStart + size), chapters);
                if (!chapters.isEmpty()) return chapters;
            }
            if (id == ID_SEGMENT) {
                pos[0] = bodyStart;
                continue;
            }
            pos[0] = bodyStart + (int) size;
            if (pos[0] <= elementStart) break;
        }
        List<MediaChapter> fallback = scanForChapters(data);
        return fallback;
    }

    private static List<MediaChapter> scanForChapters(byte[] data) {
        for (int i = 0; i + 8 < data.length; i++) {
            if ((data[i] & 0xFF) == 0x10 && (data[i + 1] & 0xFF) == 0x43
                    && (data[i + 2] & 0xFF) == 0xA7 && (data[i + 3] & 0xFF) == 0x70) {
                int[] pos = {i + 4};
                long size = readSize(data, pos);
                if (size < 0) size = data.length - pos[0];
                int end = (int) Math.min(data.length, pos[0] + size);
                List<MediaChapter> chapters = new ArrayList<>();
                parseChapters(data, pos[0], end, chapters);
                if (!chapters.isEmpty()) return chapters;
            }
        }
        return Collections.emptyList();
    }

    private static void parseChapters(byte[] data, int start, int end, List<MediaChapter> out) {
        int[] pos = {start};
        while (pos[0] < end) {
            long id = readId(data, pos);
            if (id < 0) return;
            long size = readSize(data, pos);
            if (size < 0) return;
            int bodyStart = pos[0];
            int bodyEnd = (int) Math.min(end, bodyStart + size);
            if (id == ID_EDITION) {
                parseEdition(data, bodyStart, bodyEnd, out);
            }
            pos[0] = bodyEnd;
        }
    }

    private static void parseEdition(byte[] data, int start, int end, List<MediaChapter> out) {
        int[] pos = {start};
        while (pos[0] < end) {
            long id = readId(data, pos);
            if (id < 0) return;
            long size = readSize(data, pos);
            if (size < 0) return;
            int bodyStart = pos[0];
            int bodyEnd = (int) Math.min(end, bodyStart + size);
            if (id == ID_ATOM) {
                parseAtom(data, bodyStart, bodyEnd, out);
            }
            pos[0] = bodyEnd;
        }
    }

    private static void parseAtom(byte[] data, int start, int end, List<MediaChapter> out) {
        int[] pos = {start};
        long timeNs = -1L;
        String title = "";
        while (pos[0] < end) {
            long id = readId(data, pos);
            if (id < 0) break;
            long size = readSize(data, pos);
            if (size < 0) break;
            int bodyStart = pos[0];
            int bodyEnd = (int) Math.min(end, bodyStart + size);
            if (id == ID_TIME_START) {
                timeNs = readUInt(data, bodyStart, bodyEnd);
            } else if (id == ID_DISPLAY) {
                title = parseDisplay(data, bodyStart, bodyEnd);
            }
            pos[0] = bodyEnd;
        }
        if (timeNs >= 0) {
            out.add(new MediaChapter(title, timeNs / 1000L));
        }
    }

    private static String parseDisplay(byte[] data, int start, int end) {
        int[] pos = {start};
        while (pos[0] < end) {
            long id = readId(data, pos);
            if (id < 0) return "";
            long size = readSize(data, pos);
            if (size < 0) return "";
            int bodyStart = pos[0];
            int bodyEnd = (int) Math.min(end, bodyStart + size);
            if (id == ID_STRING) {
                return new String(data, bodyStart, bodyEnd - bodyStart, java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            pos[0] = bodyEnd;
        }
        return "";
    }

    private static long readUInt(byte[] data, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) value = (value << 8) | (data[i] & 0xFF);
        return value;
    }

    private static long readId(byte[] data, int[] pos) {
        if (pos[0] >= data.length) return -1;
        int first = data[pos[0]] & 0xFF;
        int length = 1;
        int mask = 0x80;
        while ((first & mask) == 0 && length < 4) {
            length++;
            mask >>= 1;
        }
        if ((first & mask) == 0) return -1;
        if (pos[0] + length > data.length) return -1;
        long value = 0;
        for (int i = 0; i < length; i++) value = (value << 8) | (data[pos[0]++] & 0xFF);
        return value;
    }

    private static long readSize(byte[] data, int[] pos) {
        if (pos[0] >= data.length) return -1;
        int first = data[pos[0]] & 0xFF;
        int length = 1;
        int mask = 0x80;
        while ((first & mask) == 0 && length <= 8) {
            length++;
            mask >>= 1;
        }
        if ((first & mask) == 0) return -1;
        if (pos[0] + length > data.length) return -1;
        pos[0]++;
        long value = first & (mask - 1);
        boolean allOnes = value == (mask - 1);
        for (int i = 1; i < length; i++) {
            int b = data[pos[0]++] & 0xFF;
            if (b != 0xFF) allOnes = false;
            value = (value << 8) | b;
        }
        return allOnes ? -1 : value;
    }
}
