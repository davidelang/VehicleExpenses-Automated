# Get started: MinIO / S3-compatible photo backup

**In app:** Photo backup → **S3** (top tier), not Other.

Self-hosted object storage that speaks the **S3 API** (MinIO, SeaweedFS S3 gateway, garage, etc.).

## Minimal MinIO setup

1. Install MinIO (single node or cluster) per MinIO docs.  
2. Create a bucket e.g. `vehicle-expenses`.  
3. Create an access key + secret with read/write on that bucket.  
4. Expose the API endpoint over HTTPS (or LAN HTTP for testing only).

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Provider | **MinIO** or **Other S3-compatible** |
| Access Key ID | MinIO access key |
| Secret Access Key | MinIO secret |
| Region | Often `us-east-1` or MinIO’s configured region |
| Endpoint | `https://minio.example.com` (required for MinIO) |
| Bucket | `vehicle-expenses` |
| Path prefix | `VehicleExpenses/photos` |

Save credentials, then **Test connection**.

## Tips

- Path-style vs virtual-host style: if list/upload fails, try the other provider preset or check MinIO console URL style.  
- Do not use public anonymous write buckets.

## Vendor docs

- MinIO: https://min.io/docs/minio/linux/index.html  
- rclone S3: https://rclone.org/s3/  
