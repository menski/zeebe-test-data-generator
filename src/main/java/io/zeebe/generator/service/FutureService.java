package io.zeebe.generator.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

@Service
public class FutureService {

  private Map<String, Future<Void>> futures = new HashMap<>();

  public String putFuture(Future<Void> future) {
    String uuid = UUID.randomUUID().toString();
    futures.put(uuid, future);
    return uuid;
  }

  public boolean checkFuture(String uuid) {
    Future<Void> future = futures.get(uuid);
    if (future != null) {
      if (future.isDone()) {
        futures.remove(uuid);
        return true;
      }
      else {
        return false;
      }
    }
    else {
      return true;
    }
  }

}
