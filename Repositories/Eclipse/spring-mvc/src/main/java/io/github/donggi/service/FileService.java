package io.github.donggi.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    public String appropriateFileName(HttpServletRequest request, String fileName) throws UnsupportedEncodingException {
        var browser = request.getHeader("User-Agent");
        if (browser.contains("MSIE") || browser.contains("Trident") || browser.contains("Chrome"))
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        else
            return new String(fileName.getBytes(), "ISO-8859-1");
    }

    public void download(HttpServletRequest request,
            HttpServletResponse response, MultipartFile file) throws IOException {
        response.setContentType("application/download;");
        response.setContentLengthLong(file.getSize());

        response.setHeader("Content-Transfer-Encoding", "binary;");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + appropriateFileName(request, file.getOriginalFilename()) + "\";");
        file.getInputStream().transferTo(response.getOutputStream());
        response.getOutputStream().close();
    }

    public void download(HttpServletRequest request,
            HttpServletResponse response, List<MultipartFile> files) throws IOException {
        final var BOUNDARY = "-----------------------------" + System.nanoTime();
        response.setContentType("multipart/x-mixed-replace;boundary=" + BOUNDARY);
        var out = response.getOutputStream();
        for(var file : files) {
            out.println(BOUNDARY);
            out.println("Content-type:" + file.getContentType());
            out.println("Content-Disposition:attachment;filename=\"" + appropriateFileName(request, file.getOriginalFilename()) + "\"");
            file.getInputStream().transferTo(out);
            out.println();
        }
        out.print(BOUNDARY + "--");
        out.close();
    }

    public void downloadZip(HttpServletResponse response, List<MultipartFile> files) throws IOException {
        response.setContentType("application/zip;");
        response.setHeader("Content-Transfer-Encoding", "binary;");
        response.setHeader("Content-Disposition", "attachment; filename=\"download.zip\";");

        var out = new ZipOutputStream(response.getOutputStream());
        for (var file : files) {
            out.putNextEntry(new ZipEntry(file.getOriginalFilename()));
            file.getInputStream().transferTo(out);
            out.closeEntry();
        }
        out.close();
    }

}
