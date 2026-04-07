provider "aws" {
  region = var.region
}

# --- VPC Setup ---
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.0.0"

  name = "${var.cluster_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["${var.region}a", "${var.region}b", "${var.region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  database_subnets = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  create_database_subnet_group = true
}

# --- AWS RDS (Production Database) Setup ---
module "db" {
  source  = "terraform-aws-modules/rds/aws"
  version = "6.1.1"

  identifier = "ecommerce-postgres-prod"

  engine               = "postgres"
  engine_version       = "15"
  family               = "postgres15"
  major_engine_version = "15"
  instance_class       = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100

  db_name  = "mydb"
  username = "admin"
  port     = 5432

  multi_az               = false
  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [module.vpc.default_security_group_id]

  maintenance_window = "Mon:00:00-Mon:03:00"
  backup_window      = "03:00-06:00"

  # Important parameter in prod to ensure actual snapshots happen
  skip_final_snapshot = true 
}

# --- EKS Cluster Setup ---
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.16.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.30"
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnets

  eks_managed_node_groups = {
    general = {
      desired_size = 2
      min_size     = 1
      max_size     = 4

      instance_types = ["t3.medium"]
      capacity_type  = "ON_DEMAND"
    }
  }

  cluster_endpoint_public_access = true
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}
output "kubeconfig_cmd" {
  value = "aws eks --region ${var.region} update-kubeconfig --name ${var.cluster_name}"
}
output "rds_endpoint" {
  value = module.db.db_instance_endpoint
}
