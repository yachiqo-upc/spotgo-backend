package com.yachiqo.spotgo.monitoring.infrastructure.storage;
import com.yachiqo.spotgo.monitoring.application.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalEvidenceImageStorageTest {
 @TempDir Path directory;
 byte[] jpeg() throws Exception {
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
  try(var out=new ByteArrayOutputStream()){javax.imageio.ImageIO.write(image,"jpeg",out);return out.toByteArray();}
 }
 @Test void storesOriginalJpegBytesWithGeneratedNameAndReadsThem() throws Exception {
  var storage=new LocalEvidenceImageStorage(directory.toString());byte[] bytes=jpeg();var key=storage.store(bytes);
  assertTrue(key.endsWith(".jpg"));assertArrayEquals(bytes,storage.read(key).bytes());assertEquals("image/jpeg",storage.read(key).contentType());
  storage.remove(key);assertThrows(MonitoringException.class,()->storage.read(key));
 }
 @Test void rejectsNonImagesEmptyFilesAndOversizedContent() {
  var storage=new LocalEvidenceImageStorage(directory.toString());
  assertEquals(415,assertThrows(MonitoringException.class,()->storage.store("<svg onload='alert(1)'/>".getBytes())).getStatusCode().value());
  assertThrows(IllegalArgumentException.class,()->storage.store(new byte[0]));
  assertEquals(413,assertThrows(MonitoringException.class,()->storage.store(new byte[EvidenceImageStorage.MAX_BYTES+1])).getStatusCode().value());
 }
 @Test void doesNotReadArbitraryPaths() {
  var storage=new LocalEvidenceImageStorage(directory.toString());
  assertEquals(404,assertThrows(MonitoringException.class,()->storage.read("../application.properties")).getStatusCode().value());
  assertEquals(404,assertThrows(MonitoringException.class,()->storage.read("/etc/passwd")).getStatusCode().value());
 }
 @Test void unavailableStorageDoesNotPretendImageWasSaved() throws Exception {
  Path blocked=directory.resolve("not-a-directory");Files.writeString(blocked,"existing file");
  var storage=new LocalEvidenceImageStorage(blocked.toString());byte[] bytes=jpeg();
  assertEquals(503,assertThrows(MonitoringException.class,()->storage.store(bytes)).getStatusCode().value());
  assertEquals("existing file",Files.readString(blocked));
 }
}
