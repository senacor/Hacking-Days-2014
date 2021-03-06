package com.senacor.hdays2014.hazelCollector;

import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import com.hazelcast.config.Config;
import com.hazelcast.config.InterfacesConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.senacor.hdays2014.hazelCollector.helper.ClusterAddress;
import jdk.nashorn.internal.parser.JSONParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;

@Component
public class HazelCollector {

  private Log log = LogFactory.getLog(HazelCollector.class);
  private AtomicInteger counter = new AtomicInteger(0);
  private int lastMapEntry = 0;

  @Autowired
  ClusterAddress clusterAddress;

  private HazelcastInstance instance;
  private Map<String, Lock> lockMap = new HashMap<String, Lock>();


  public void init() {
    Config cfg = new Config();

    TcpIpConfig config = cfg.getNetworkConfig().getJoin().getTcpIpConfig();
    cfg.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);

    config.setEnabled(true);

    for (String s: clusterAddress.getAddresses()) {
      config.addMember(s);
    }


    instance = Hazelcast.newHazelcastInstance(cfg);
    Map<Object,Object> keys = instance.getMap("lastKey");
    if(keys!=null){
        final int counterValue =  getMaxKey(keys, "t_temp", "t_hum", "t_light", "t_sound");
        counter.set(counterValue);
    }
    }

  private int getMaxKey(Map<Object, Object> keys, String... queues){
      int result = 0;
      for(String queue : queues){
          final int value = getNotNUllValue(keys, queue);
          if(value>result){
              result = value;
          }
      }
      return result;
  }

  private Integer getNotNUllValue(final Map<Object,Object> keys, String queue){
      Integer value =(Integer) keys.get(queue);
      return value ==null?0: value;
  }

  public Event parseEvent(String data) {
    JSONObject json = new JSONObject(data);

    Event event = new Event();
    event.setValue(String.valueOf(json.get("value")));
    event.setUnit(json.getString("unit"));
    event.setTimestamp(new Date());

    return event;
  }

  public void addEvent(String topic, String data) {
    if (instance == null) {
      init();
    }

    Event event = parseEvent(data);

    instance.getMap(topic).put(counter.incrementAndGet(), event);
  }

  public int getIndex(String topic) {
    Map<String, Integer> indexMap = instance.getMap("lastKey");

    int index = indexMap.get(topic);
    return index;
  }


  public boolean getLock(String topic) {
    if (instance == null) {
      init();
    }

    Lock lock = instance.getLock(topic);
    try {
      if (lock.tryLock (5000, TimeUnit.MILLISECONDS)) {
        lockMap.put(topic, lock);
        return true;
      }
    } catch (InterruptedException e) {
      log.error("Could not get lock " + topic);
    }

    return false;
  }

  public IMap<Integer, Event> getMap(String topic) {
    if (instance == null) {
      init();
    }

    return instance.getMap(topic);
  }

  public HazelcastInstance getInstance() {
      if (instance == null) {
          init();
      }
      return instance;
  }
}
