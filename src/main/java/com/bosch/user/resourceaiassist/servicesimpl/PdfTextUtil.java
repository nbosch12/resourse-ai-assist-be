package com.bosch.user.resourceaiassist.servicesimpl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfTextUtil {
    public static List<Page> extractPages(byte[] pdf) throws Exception {
        List<Page> pages = new ArrayList<>();
        try (var doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            PDFTextStripper s = new PDFTextStripper();
            for (int p=1;p<=doc.getNumberOfPages();p++){
                s.setStartPage(p); s.setEndPage(p);
                pages.add(new Page(p, s.getText(doc)));
            }
        }
        return pages;
    }
    public record Page(int number, String text) {}
}
