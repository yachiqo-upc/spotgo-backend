package com.yachiqo.spotgo.monitoring.infrastructure.storage;

import com.yachiqo.spotgo.monitoring.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class LocalEvidenceImageStorage implements EvidenceImageStorage {
 private static final Logger log=LoggerFactory.getLogger(LocalEvidenceImageStorage.class);
 private final Path root;
 public LocalEvidenceImageStorage(@Value("${app.monitoring.evidence-directory:./storage/evidence}") String directory){
  root=Path.of(directory).toAbsolutePath().normalize();
 }
 private Path file(String name){
  if(name==null||!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"))throw new MonitoringException(HttpStatus.NOT_FOUND,"Evidence image not found.");
  Path file=root.resolve(name).normalize();
  if(!file.startsWith(root)||Files.isSymbolicLink(file))throw new MonitoringException(HttpStatus.NOT_FOUND,"Evidence image not found.");
  return file;
 }
 private String imageExtension(byte[] bytes){
  if(bytes==null||bytes.length==0)throw new IllegalArgumentException("Select an image file.");
  if(bytes.length>MAX_BYTES)throw new MonitoringException(HttpStatus.PAYLOAD_TOO_LARGE,"The image cannot exceed 5 MB.");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new MonitoringException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"The file must contain a valid JPEG or PNG image.");
   var reader=readers.next();
   try{
    reader.setInput(input,true,true);
    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    if(!Set.of("jpeg","jpg","png").contains(format))throw new MonitoringException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Only JPEG and PNG images are supported.");
    int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1||height<1||(long)width*height>20_000_000)throw new IllegalArgumentException("The image cannot exceed 20 million pixels.");
    if(reader.read(0)==null)throw new IllegalArgumentException("The evidence image is unreadable.");
    return format.equals("png")?"png":"jpg";
   }finally{reader.dispose();}
  }catch(IOException e){throw new IllegalArgumentException("The image file is damaged or incomplete.");}
 }
 @Override public String store(byte[] bytes){
  String name=UUID.randomUUID()+"."+imageExtension(bytes);Path destination=file(name);
  try{Files.createDirectories(root);Files.write(destination,bytes,StandardOpenOption.CREATE_NEW);return name;}
  catch(FileAlreadyExistsException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"A file name could not be assigned. Retry the upload.");}
  catch(IOException e){remove(name);throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"The evidence image could not be saved.");}
 }
 @Override public StoredImage read(String name){
  Path image=file(name);
  try{
   if(!Files.isRegularFile(image)||Files.size(image)>MAX_BYTES)throw new MonitoringException(HttpStatus.NOT_FOUND,"Evidence image not found.");
   return new StoredImage(name,name.endsWith(".png")?"image/png":"image/jpeg",Files.readAllBytes(image));
  }catch(NoSuchFileException e){throw new MonitoringException(HttpStatus.NOT_FOUND,"Evidence image not found.");}
  catch(IOException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"The image could not be read.");}
 }
 @Override public void remove(String name){
  if(name==null)return;
  try{Files.deleteIfExists(file(name));}
  catch(IOException|MonitoringException e){log.warn("A replaced or rolled-back evidence file could not be deleted.");}
 }
}
