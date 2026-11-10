package com.yachiqo.spotgo.monitoring.application;
/** Stores actual image bytes; references remain in occupancy_alerts.evidence_ref. */
public interface EvidenceImageStorage {
 int MAX_BYTES=5*1024*1024;
 record StoredImage(String fileName,String contentType,byte[] bytes){}
 String store(byte[] image);
 StoredImage read(String fileName);
 void remove(String fileName);
}
