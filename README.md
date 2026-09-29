
# sdec-fileservice-api-alpha

## AWS Service
AWS services are emulated in Docker using [SeaweedFS](https://github.com/seaweedfs/seaweedfs), for which there is a `docker-compose.yml` in the
`docker` folder. It will use `seaweedfs-data` folder to store files (which you might want to clean regularly).
 
## File Upload to S3 (normal)
Use `http://localhost:4510/sdec-fileservice-api/file-upload`

To see the results in S3:
```shell
export AWS_ACCESS_KEY_ID=admin
export AWS_SECRET_ACCESS_KEY=secret

aws --endpoint-url http://localhost:8333 s3 ls s3://sdec-files/uploads/
```

## File Upload to S3 (Multipart)
Use `http://localhost:4510/sdec-fileservice-api/multipart-upload`

To see the results in S3:
```shell
export AWS_ACCESS_KEY_ID=admin
export AWS_SECRET_ACCESS_KEY=secret

aws --endpoint-url http://localhost:8333 s3 ls s3://sdec-uploads/uploads/
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").