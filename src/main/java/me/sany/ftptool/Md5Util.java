/*
 * Copyright 2022 Shang Yehua <niceshang@outlook.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package me.sany.ftptool;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.commons.codec.digest.DigestUtils;

/**
 *
 * TODO Md5Util说明
 *
 * @author Shang Yehua <niceshang@outlook.com>
 * @since 2022-09-27  17:19
 *
 */
public final class Md5Util {

    public static void createMd5FileFor(Path path) throws IOException {
        Path md5FilePath = path.resolveSibling(path.getFileName().toString() + ".md5");
        Files.deleteIfExists(md5FilePath);
        String md5 = DigestUtils.md5Hex(Files.newInputStream(path)).toUpperCase();
        Files.write(md5FilePath, md5.getBytes("UTF-8"));
    }

    public static boolean check(Path path) throws IOException {
        Path md5FilePath = path.resolveSibling(path.getFileName().toString() + ".md5");
        String fileMd5 = new String(Files.readAllBytes(md5FilePath), Charset.forName("UTF-8"))
                .trim().toUpperCase();
        String md5 = DigestUtils.md5Hex(Files.newInputStream(path)).toUpperCase();
        return md5.equals(fileMd5);
    }

    public static void makeMd5Files(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Path:[" + path + "] does not exists.");
        }
        if (Files.isRegularFile(path)) {
            if(path.getFileName().toString().toLowerCase().endsWith(".md5")){
                return;
            }
            createMd5FileFor(path);
        } else {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if(!file.getFileName().toString().startsWith(".") && !file.getFileName().toString().toLowerCase().endsWith(".md5")){
                        createMd5FileFor(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
