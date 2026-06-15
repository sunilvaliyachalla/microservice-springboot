variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "ecommerce-production-cluster"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to reach the public EKS API endpoint. Restrict to trusted networks; do not use 0.0.0.0/0 in production."
  type        = list(string)
  default     = []
}
