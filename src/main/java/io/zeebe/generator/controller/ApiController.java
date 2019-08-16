package io.zeebe.generator.controller;

import io.zeebe.generator.service.FutureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

  @Autowired
  private FutureService futureService;

  @GetMapping("/check-future")
  public boolean checkFuture(@RequestParam("future") String futureId) {
    return futureService.checkFuture(futureId);
  }
}
