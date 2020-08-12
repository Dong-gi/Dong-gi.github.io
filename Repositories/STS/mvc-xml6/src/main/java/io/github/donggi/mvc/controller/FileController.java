package io.github.donggi.mvc.controller;

import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import io.github.donggi.mvc.service.FileService;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/file/*")
@Slf4j
public class FileController {
    @Autowired
    private FileService fileService;


    @RequestMapping(value = "/upload", method = RequestMethod.GET)
    public String upload() {
        return "upload";
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public String upload(List<MultipartFile> files) {
        files.forEach(x -> log.debug("{} : {}, {}bytes", x.getOriginalFilename(), x.getContentType(), x.getSize()));
        return "upload";
    }

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    public String download() {
        return "download";
    }

    @RequestMapping(value = "/download", method = RequestMethod.POST)
    public void download(HttpServletRequest request,
            HttpServletResponse response, List<MultipartFile> files) throws IOException {
        if(files.size() > 1)
            fileService.download(request, response, files);
        else if(files.size() > 0)
            fileService.download(request, response, files.get(0));
    }

    @RequestMapping(value = "/downloadZip", method = RequestMethod.POST)
    public void downloadZip(HttpServletRequest request,
            HttpServletResponse response, List<MultipartFile> files) throws IOException {
        fileService.downloadZip(response, files);
    }
}
