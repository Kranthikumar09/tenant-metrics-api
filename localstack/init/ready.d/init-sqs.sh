#!/bin/bash
set -euo pipefail
awslocal sqs create-queue --queue-name accepted-events
