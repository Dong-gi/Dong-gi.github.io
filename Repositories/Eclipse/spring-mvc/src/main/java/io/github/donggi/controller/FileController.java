package io.github.donggi.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import io.github.donggi.service.FileService;

@Controller
@RequestMapping("/file/*")
public class FileController {

    @Autowired
    private FileService fileService;

    @RequestMapping(value = "/upload", method = RequestMethod.GET)
    public String upload() {
        return "file/upload";
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public ModelAndView upload(List<MultipartFile> files) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", files.stream().map(x -> String.format("'%s' : %s, %dbytes", x.getOriginalFilename(), x.getContentType(), x.getSize())).collect(Collectors.joining("<br>")));
        return mv;
    }
/*
 * 전체 컨트롤러에 적용되는 SimpleMappingExceptionResolver 대신에,
 * 컨트롤러 클래스가 org.springframework.web.servlet.HandlerExceptionResolver 인터페이스를 구현할 수 있다.

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", ex.getMessage());
        return mv;
    }
*/

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    public String download() {
        return "file/download";
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
