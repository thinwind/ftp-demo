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

import java.util.List;
import java.util.function.Consumer;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.commons.lang3.exception.ExceptionUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.Data;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

/**
 *
 * TODO FtpOperation说明
 *
 * @author Shang Yehua <niceshang@outlook.com>
 * @since 2022-09-27  14:26
 *
 */
@Component
public class FtpOperation {

    @Autowired
    FtpConfiguration conf;

    private final static String MSG_TEMPLATE = "---------[%s] %s";

    public Result download(String localPath, String remotePath) {
        Result result = new Result();
        try {
            RmDir.rmdir(localPath);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        result.setLocalPath(localPath);
        result.setRemotePath(remotePath);
        result.setOperation("download");
        Timer timer = new Timer();
        System.out.println(msg("Download", "Start ..."));
        try {
            Result getResult = get(localPath, remotePath);
            System.out.println(msg("Download", "Completed."));
            System.out.println(msg("Download", "Duration: " + timer.duration() + "ms."));
            result.setSuccess(getResult.success);
            result.setMsg(getResult.msg);
        } catch (Exception e) {
            result.setMsg(ExceptionUtils.getStackTrace(e));
            result.setSuccess(false);
            result.complete();
            return result;
        }
        result.setSuccess(true);
        System.out.println(msg("Check Md5", "Start ..."));
        Path local = Paths.get(localPath);
        if (Files.isRegularFile(local)) {
            boolean matched;
            try {
                matched = Md5Util.check(local);
                if (!matched) {
                    result.setSuccess(false);
                    result.setMsg("Md5 check failed");
                }
            } catch (IOException e) {
                result.setMsg(ExceptionUtils.getStackTrace(e));
                result.setSuccess(false);
                result.complete();
                return result;
            }
        } else {
            try {
                Files.walkFileTree(local, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (file.getFileName().toString().toLowerCase().endsWith(".md5")) {
                            String md5Name = file.getFileName().toString();
                            Path filePath =
                                    file.resolveSibling(md5Name.substring(0, md5Name.length() - 4));
                            boolean matched = Md5Util.check(filePath);
                            if (!matched) {
                                result.setSuccess(false);
                                result.setMsg(
                                        "File[" + filePath.toAbsolutePath() + "] Md5 check failed");
                                return FileVisitResult.TERMINATE;
                            } else {
                                Files.delete(file);
                                System.out.println(msg("Check Md5",
                                        "[" + file.toAbsolutePath() + " removed.]"));
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                result.setMsg(ExceptionUtils.getStackTrace(e));
                result.setSuccess(false);
            }
        }
        System.out.println(msg("Check Md5", "End."));
        System.out.println(msg("Check Md5", "Duration: " + timer.duration() + "ms."));
        result.complete();
        return result;
    }

    public Result get(String localPath, String remotePath) {
        Result result = operation(localPath, remotePath, (sftp) -> {
            try {
                sftp.get(remotePath, localPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        result.setOperation("get");
        result.complete();
        return result;
    }

    public Result upload(String localPath, String remotePath) {
        Result result = new Result();
        result.setLocalPath(localPath);
        result.setRemotePath(remotePath);
        result.setOperation("upload");
        Timer timer = new Timer();
        Path local = Paths.get(localPath);
        System.out.println(msg("Create Md5", "Start ..."));
        try {
            Md5Util.makeMd5Files(local);
            System.out.println(msg("Create Md5", "Completed."));
            System.out.println(msg("Create Md5", "Duration: " + timer.duration() + "ms."));
        } catch (Exception e) {
            result.setMsg(ExceptionUtils.getStackTrace(e));
            result.setSuccess(false);
            result.complete();
            return result;
        }
        System.out.println(msg("Upload to ftp", "Start ..."));
        Result putResult = put(localPath, remotePath);
        System.out.println(msg("Upload to ftp", "End."));
        System.out.println(msg("Upload to ftp", "Duration: " + timer.duration() + "ms."));
        result.setSuccess(putResult.success);
        result.setMsg(putResult.msg);
        result.complete();
        return result;
    }

    String msg(Object... strs) {
        return String.format(MSG_TEMPLATE, strs);
    }

    public Result put(String localPath, String remotePath) {
        Result result = operation(localPath, remotePath, (sftp) -> {
            Path local = Paths.get(localPath);
            if (!Files.exists(local)) {
                throw new RuntimeException(
                        "Local path:" + local.toAbsolutePath() + " does not exists.");
            }
            try {
                sftp.mkdirs(Paths.get(remotePath).toAbsolutePath().getParent().toString());
                sftp.put(localPath, remotePath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        result.setOperation("put");
        result.complete();
        return result;
    }

    public Result remove(String remotePath) {
        Result result = operation("", remotePath, (sftp) -> {
            try {
                FileAttributes attr = sftp.lstat(remotePath);
                if (attr.getType() == FileMode.Type.DIRECTORY) {
                    rmFtpDir(sftp, remotePath);
                } else {
                    sftp.rm(remotePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        result.setOperation("delete");
        result.complete();
        return result;
    }

    private void rmFtpDir(SFTPClient sftp, String remotePath) throws IOException {
        List<RemoteResourceInfo> subfiles = sftp.ls(remotePath);
        for (RemoteResourceInfo fileInfo : subfiles) {
            if (!fileInfo.isDirectory()) {
                sftp.rm(fileInfo.getPath());
            } else {
                rmFtpDir(sftp, fileInfo.getPath());
            }
            System.out.println(msg("Remote delete", fileInfo.getPath()));
        }
        sftp.rmdir(remotePath);
    }

    private Result operation(String localPath, String remotePath, Consumer<SFTPClient> consumer) {
        Result result = new Result();
        result.setLocalPath(localPath);
        result.setRemotePath(remotePath);
        final SSHClient ssh = new SSHClient();
        try {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.connect(conf.getAddr(), conf.getPort());
            ssh.authPassword(conf.getUser(), conf.getPass());

            final SFTPClient sftp = ssh.newSFTPClient();
            try {
                consumer.accept(sftp);
                result.setSuccess(true);
            } catch (Exception e) {
                result.setMsg(ExceptionUtils.getStackTrace(e));
                result.setSuccess(false);
            } finally {
                sftp.close();
            }
        } catch (Exception e) {
            result.setMsg(ExceptionUtils.getStackTrace(e));
            result.setSuccess(false);

        } finally {
            try {
                ssh.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                result.setMsg(ExceptionUtils.getStackTrace(e));
            }
            try {
                ssh.close();
            } catch (IOException e) {
                e.printStackTrace();
                result.setMsg(ExceptionUtils.getStackTrace(e));
            }
        }
        return result;
    }


    @Data
    static class Result {

        private boolean success;

        private String operation;

        private String localPath;

        private String remotePath;

        private String msg;

        private long expense;

        @JsonIgnore
        private transient Timer timer = new Timer();

        public void complete() {
            expense = timer.duration();
        }

    }
}
