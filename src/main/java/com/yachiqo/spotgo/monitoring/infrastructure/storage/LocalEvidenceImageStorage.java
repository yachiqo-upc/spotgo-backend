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
  if(name==null||!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"))throw new MonitoringException(HttpStatus.NOT_FOUND,"Imagen inexistente");
  Path file=root.resolve(name).normalize();
  if(!file.startsWith(root)||Files.isSymbolicLink(file))throw new MonitoringException(HttpStatus.NOT_FOUND,"Imagen inexistente");
  return file;
 }
 private String imageExtension(byte[] bytes){
  if(bytes==null||bytes.length==0)throw new IllegalArgumentException("Selecciona un archivo de imagen");
  if(bytes.length>MAX_BYTES)throw new MonitoringException(HttpStatus.PAYLOAD_TOO_LARGE,"La imagen no puede superar 5 MB");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new MonitoringException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"El archivo debe ser una imagen JPEG o PNG real");
   var reader=readers.next();
   try{
    reader.setInput(input,true,true);
    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    if(!Set.of("jpeg","jpg","png").contains(format))throw new MonitoringException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Solo se admiten JPEG y PNG");
    int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1||height<1||(long)width*height>20_000_000)throw new IllegalArgumentException("La imagen no puede superar 20 millones de pixeles");
    if(reader.read(0)==null)throw new IllegalArgumentException("Imagen ilegible");
    return format.equals("png")?"png":"jpg";
   }finally{reader.dispose();}
  }catch(IOException e){throw new IllegalArgumentException("El archivo de imagen esta dañado o incompleto");}
 }
 @Override public String store(byte[] bytes){
  String name=UUID.randomUUID()+"."+imageExtension(bytes);Path destination=file(name);
  try{Files.createDirectories(root);Files.write(destination,bytes,StandardOpenOption.CREATE_NEW);return name;}
  catch(FileAlreadyExistsException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo asignar un nombre de archivo; reintenta la subida");}
  catch(IOException e){remove(name);throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo guardar la imagen de evidencia");}
 }
 @Override public StoredImage read(String name){
  Path image=file(name);
  try{
   if(!Files.isRegularFile(image)||Files.size(image)>MAX_BYTES)throw new MonitoringException(HttpStatus.NOT_FOUND,"Imagen inexistente");
   return new StoredImage(name,name.endsWith(".png")?"image/png":"image/jpeg",Files.readAllBytes(image));
  }catch(NoSuchFileException e){throw new MonitoringException(HttpStatus.NOT_FOUND,"Imagen inexistente");}
  catch(IOException e){throw new MonitoringException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo leer la imagen");}
 }
 @Override public void remove(String name){
  if(name==null)return;
  try{Files.deleteIfExists(file(name));}
  catch(IOException|MonitoringException e){log.warn("No se pudo eliminar un archivo de evidencia reemplazado o revertido");}
 }
}
