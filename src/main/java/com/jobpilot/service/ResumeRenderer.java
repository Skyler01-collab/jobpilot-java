package com.jobpilot.service;

import com.jobpilot.model.Profile;
import com.jobpilot.model.TailoredResume;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;

/** Plain, single-column, ATS-friendly output: no tables, no images, standard headings. */
@Service
public class ResumeRenderer {

    private final CharsetEncoder win1252 = Charset.forName("windows-1252").newEncoder();

    // ---------------- PDF ----------------
    public byte[] pdf(TailoredResume r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 42, 42, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font nameF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font bodyF = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font smallF = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph name = new Paragraph(clean(r.fullName()), nameF);
            name.setAlignment(Element.ALIGN_CENTER);
            doc.add(name);
            if (!r.headline().isBlank()) {
                Paragraph h = new Paragraph(clean(r.headline()), bodyF);
                h.setAlignment(Element.ALIGN_CENTER);
                doc.add(h);
            }
            Paragraph contact = new Paragraph(clean(r.contactLine()), smallF);
            contact.setAlignment(Element.ALIGN_CENTER);
            contact.setSpacingAfter(4);
            doc.add(contact);

            if (!r.summary().isBlank()) {
                heading(doc, "SUMMARY", headF);
                doc.add(new Paragraph(clean(r.summary()), bodyF));
            }
            if (!r.skills().isEmpty()) {
                heading(doc, "SKILLS", headF);
                doc.add(new Paragraph(clean(String.join(", ", r.skills())), bodyF));
            }
            if (!r.experience().isEmpty()) {
                heading(doc, "EXPERIENCE", headF);
                for (Profile.Experience e : r.experience()) {
                    Paragraph p = new Paragraph();
                    p.setSpacingBefore(4);
                    p.add(new Chunk(clean(join(", ", e.getTitle(), e.getCompany())), boldF));
                    String dates = join(" - ", e.getStart(), e.getEnd());
                    if (!dates.isBlank()) p.add(new Chunk("   " + clean(dates), smallF));
                    doc.add(p);
                    for (String b : e.getBullets()) doc.add(bullet(b, bodyF));
                }
            }
            if (!r.projects().isEmpty()) {
                heading(doc, "PROJECTS", headF);
                for (Profile.Project pr : r.projects()) {
                    Paragraph p = new Paragraph();
                    p.setSpacingBefore(4);
                    p.add(new Chunk(clean(pr.getName()), boldF));
                    if (!pr.getTech().isBlank()) p.add(new Chunk("   " + clean(pr.getTech()), smallF));
                    doc.add(p);
                    for (String b : pr.getBullets()) doc.add(bullet(b, bodyF));
                }
            }
            if (!r.education().isEmpty()) {
                heading(doc, "EDUCATION", headF);
                for (Profile.Education e : r.education()) {
                    Paragraph p = new Paragraph(clean(join(", ", e.getDegree(), e.getSchool(), e.getYear())), bodyF);
                    p.setSpacingBefore(2);
                    doc.add(p);
                }
            }
            if (!r.certifications().isEmpty()) {
                heading(doc, "CERTIFICATIONS", headF);
                for (String c : r.certifications()) doc.add(bullet(c, bodyF));
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private void heading(Document doc, String text, Font f) throws DocumentException {
        Paragraph p = new Paragraph(text, f);
        p.setSpacingBefore(10);
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private Paragraph bullet(String text, Font f) {
        Paragraph p = new Paragraph("\u2022 " + clean(text), f);
        p.setIndentationLeft(12);
        p.setFirstLineIndent(-8);
        p.setSpacingBefore(1);
        return p;
    }

    // ---------------- DOCX ----------------
    public byte[] docx(TailoredResume r) {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            line(d, r.fullName(), true, 18, true);
            if (!r.headline().isBlank()) line(d, r.headline(), false, 11, true);
            line(d, r.contactLine(), false, 9, true);

            if (!r.summary().isBlank()) { head(d, "SUMMARY"); line(d, r.summary(), false, 10, false); }
            if (!r.skills().isEmpty()) { head(d, "SKILLS"); line(d, String.join(", ", r.skills()), false, 10, false); }
            if (!r.experience().isEmpty()) {
                head(d, "EXPERIENCE");
                for (Profile.Experience e : r.experience()) {
                    String dates = join(" - ", e.getStart(), e.getEnd());
                    line(d, join(", ", e.getTitle(), e.getCompany()) + (dates.isBlank() ? "" : "   " + dates), true, 10, false);
                    for (String b : e.getBullets()) bullet(d, b);
                }
            }
            if (!r.projects().isEmpty()) {
                head(d, "PROJECTS");
                for (Profile.Project pr : r.projects()) {
                    line(d, pr.getName() + (pr.getTech().isBlank() ? "" : "   " + pr.getTech()), true, 10, false);
                    for (String b : pr.getBullets()) bullet(d, b);
                }
            }
            if (!r.education().isEmpty()) {
                head(d, "EDUCATION");
                for (Profile.Education e : r.education()) line(d, join(", ", e.getDegree(), e.getSchool(), e.getYear()), false, 10, false);
            }
            if (!r.certifications().isEmpty()) {
                head(d, "CERTIFICATIONS");
                for (String c : r.certifications()) bullet(d, c);
            }
            d.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build DOCX: " + e.getMessage(), e);
        }
    }

    private void head(XWPFDocument d, String text) {
        XWPFParagraph p = d.createParagraph();
        p.setSpacingBefore(200);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(11);
        run.setFontFamily("Calibri");
        run.setText(text);
    }

    private void line(XWPFDocument d, String text, boolean bold, int size, boolean center) {
        XWPFParagraph p = d.createParagraph();
        if (center) p.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(bold);
        run.setFontSize(size);
        run.setFontFamily("Calibri");
        run.setText(text);
    }

    private void bullet(XWPFDocument d, String text) {
        XWPFParagraph p = d.createParagraph();
        p.setIndentationLeft(360);
        XWPFRun run = p.createRun();
        run.setFontSize(10);
        run.setFontFamily("Calibri");
        run.setText("\u2022 " + text);
    }

    // ---------------- helpers ----------------
    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.strip());
        }
        return sb.toString();
    }

    /** The built-in PDF fonts only support Latin-1/Windows-1252; replace anything else. */
    private String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            synchronized (win1252) {
                sb.append(win1252.canEncode(c) ? c : ' ');
            }
        }
        return sb.toString();
    }
}
