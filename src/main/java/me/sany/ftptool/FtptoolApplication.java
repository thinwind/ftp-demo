package me.sany.ftptool;

import java.util.Date;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FtptoolApplication {

	public static void main(String[] args) {
		SpringApplication.run(FtptoolApplication.class, args);
	}

	@Bean
	public CommandLineRunner operation(FtpConfiguration conf, FtpOperation ftpOperation,
			ObjectMapper mapper) {
		return args -> {
			String opt = args[0].toLowerCase().trim();
			String localPath;
			String remotePath;
			FtpOperation.Result result;
			switch (opt) {
				case "upload":
					localPath = resolveLocalPath(conf, args[1]);
					remotePath = resolveRemotePath(conf, args[2]);
					result = ftpOperation.upload(localPath, remotePath);
					break;
				case "download":
					localPath = resolveLocalPath(conf, args[1]);
					remotePath = resolveRemotePath(conf, args[2]);
					result = ftpOperation.download(localPath, remotePath);
					break;
				case "delete":
					remotePath = resolveRemotePath(conf, args[1]);
					result = ftpOperation.remove(remotePath);
					break;
				default:
					result = new FtpOperation.Result();
					result.setSuccess(false);
					result.setMsg(args[0] + " is not supported.");
					break;
			}
			result.complete();
			System.out.println("===========>操作完成,结果:");
			System.out.println(mapper.writeValueAsString(result));
			System.exit(result.isSuccess() ? 0 : -1);
		};
	}

	private String resolveRemotePath(FtpConfiguration conf, String remotePath) {
		if (!remotePath.startsWith("/")) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
			remotePath = Paths.get(conf.getRemoteBasePath(), sdf.format(new Date()), remotePath)
					.toAbsolutePath().toString();
		}
		return remotePath;
	}

	private String resolveLocalPath(FtpConfiguration conf, String localPath) {
		if (!localPath.startsWith("/")) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
			Path parent = Paths.get(conf.getLocalBasePath(), sdf.format(new Date()));
			if(!Files.exists(parent)){
				try {
					Files.createDirectories(parent);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			localPath = parent.resolve(localPath)
					.toAbsolutePath().toString();
		}
		return localPath;
	}

}
