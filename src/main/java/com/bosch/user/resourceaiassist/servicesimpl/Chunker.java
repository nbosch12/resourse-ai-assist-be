package com.bosch.user.resourceaiassist.servicesimpl;

import java.util.ArrayList;
import java.util.List;

public class Chunker {
    public static List<Chunk> chunk(List<PdfTextUtil.Page> pages, int approxChars, int overlapChars) {
        StringBuilder buf = new StringBuilder();
        List<Chunk> out = new ArrayList<>();
        int startPage=-1, endPage=-1, startOffset=0;
        for (var pg : pages) {
            if (startPage==-1) startPage = pg.number();
            endPage = pg.number();
            String t = pg.text()==null? "" : pg.text();
            buf.append(t);
            while (buf.length() >= approxChars) {
                String win = buf.substring(0, approxChars);
                int endOffset = startOffset + approxChars;
                out.add(new Chunk(startPage, endPage, startOffset, endOffset, win));
                int keepFrom = Math.max(0, approxChars - overlapChars);
                buf.delete(0, keepFrom);
                startOffset = endOffset - overlapChars;
            }
        }
        if (buf.length()>0) out.add(new Chunk(startPage==-1?1:startPage, endPage==-1?1:endPage,
                startOffset, startOffset+buf.length(), buf.toString()));
        return out;
    }
    public record Chunk(int pageStart, int pageEnd, int offsetStart, int offsetEnd, String text) {}
}