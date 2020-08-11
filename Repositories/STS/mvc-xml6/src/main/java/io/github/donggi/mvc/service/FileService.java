package io.github.donggi.mvc.service;

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

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileService {

    public String appropriateFileName(HttpServletRequest request, String fileName) throws UnsupportedEncodingException {
        var browser = request.getHeader("User-Agent");
        log.debug(browser);
        if (browser.contains("MSIE") || browser.contains("Trident") || browser.contains("Chrome"))
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        else
            return new String(fileName.getBytes(), "ISO-8859-1");
    }

    public void download(HttpServletRequest request,
            HttpServletResponse response, MultipartFile file) throws IOException {
        response.setContentType(file.getContentType());
        response.setContentLengthLong(file.getSize());

        response.setHeader("Content-Transfer-Encoding", "binary");
        response.setHeader("Content-Disposition", "attachment;filename=\"" + appropriateFileName(request, file.getOriginalFilename()) + "\";");
        file.getInputStream().transferTo(response.getOutputStream());
        response.getOutputStream().close();
    }

    public void download(HttpServletRequest request,
            HttpServletResponse response, List<MultipartFile> files) throws IOException {
        final var BOUNDARY = "B" + System.nanoTime();
        response.setContentType("multipart/x-mixed-replace;boundary=" + BOUNDARY);
        var out = response.getOutputStream();
        out.println();
        for(var file : files) {
            out.println("--" + BOUNDARY);   // 문서 시작
            out.println("Content-Type:application/download");
            out.println("Content-Transfer-Encoding:binary");
            out.println("Content-Disposition:attachment;filename=\"" + appropriateFileName(request, file.getOriginalFilename()) + "\"");
            out.println();
            file.getInputStream().transferTo(out);
            out.println();
            out.flush();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        out.print("--" + BOUNDARY + "--");  // 문서 끝
        out.close();
    }

    public void downloadZip(HttpServletResponse response, List<MultipartFile> files) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Transfer-Encoding", "binary");
        response.setHeader("Content-Disposition", "attachment; filename=\"download.zip\"");

        var out = new ZipOutputStream(response.getOutputStream());
        for (var file : files) {
            out.putNextEntry(new ZipEntry(file.getOriginalFilename()));
            file.getInputStream().transferTo(out);
            out.closeEntry();
        }
        out.close();
    }

}
