# AI Job Application Agent

## Run locally

Requires Java 25 and MongoDB:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. The app uses `mongodb://localhost:27017/job_agent_db` by default.

Scanned/image-only PDFs are read with Tesseract OCR when PDFBox finds no embedded text. Install Tesseract OCR with the English language data. On Windows, the app automatically checks the standard Program Files and per-user installation folders; otherwise, make sure `tesseract --version` works in the terminal or set `TESSERACT_COMMAND` to the full executable path. Text-based PDFs and DOCX resumes do not require OCR. OCR can be configured with `TESSERACT_COMMAND`, `TESSERACT_LANGUAGE`, `TESSERACT_DPI`, `TESSERACT_MAXIMUM_PAGES`, and `TESSERACT_TIMEOUT_SECONDS`.

## Job sources

The discovery scan supports the existing RemoteOK and Arbeitnow public feeds, plus configured Greenhouse and Lever public job boards and HTTPS RSS/Atom feeds. Set these optional values in `.env` before starting Compose:

```text
GREENHOUSE_BOARDS=company-board-token,another-board-token
LEVER_SITES=company-site,another-site
RSS_JOB_FEEDS=Example Company=https://careers.example.com/jobs.xml;Another Company=https://another.example/jobs.rss
```

Only configure public feeds you are authorized to use. Greenhouse/Lever board IDs are public board identifiers, not account credentials. WhatsApp, email, LinkedIn, and Indeed alerts can be imported from the dashboard by entering the job details and pasting the alert text. Direct access to private channels/inboxes or LinkedIn/Indeed account data is not provided; use only platform-authorized official access if separately available. The app does not collect platform passwords or submit applications. Scans and imports deduplicate listings and rank them against the saved candidate profile.

## Apply from recruiter portal links

In the Job Discovery tab, paste up to 20 public Greenhouse or Lever board/job URLs (one HTTPS link per line) and select **Save, Scan & Rank Jobs**. Supported board URLs are saved in MongoDB for the existing opt-in six-hour background scans; a job-specific link is expanded to its public board for future scans. The initial scan of a job-specific link imports that posting; a board URL scans current public postings. New jobs are deduplicated and scored against the saved candidate profile.

Workday, LinkedIn, Indeed, and other sign-in or private career pages are not crawled. Import an individual job from those sources using the job-alert form, or add an authorized public RSS/Atom feed. The app does not request portal passwords, bypass access controls/CAPTCHAs, or invent answers to employer questions. Prepare the application, review its profile-grounded draft, and confirm the application yourself; missing required answers should be handled manually.

## Run with Docker Compose

Requires Docker Desktop with Compose:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Open `http://localhost:8080`. MongoDB data is kept in the `mongo-data` volume. The app container runs as a non-root user with a read-only root filesystem. To stop the services, run `docker compose down`; to also delete the local database volume, explicitly run `docker compose down --volumes`.

The production Spring profile hides actuator details and disables Swagger UI. `MONGODB_URI` should point to a secured production MongoDB deployment when deploying outside the local Compose setup. Email is off by default. To enable it, configure the `SMTP_*`, `AUTOMATION_EMAIL_ENABLED=true`, and `AUTOMATION_EMAIL_FROM` environment variables; never commit SMTP credentials.

The application container installs Tesseract and its English language data for scanned-PDF OCR.

The GitHub Actions workflow runs the Maven verification suite as part of the multi-stage container build and checks the `linux/amd64` image. It does not publish or deploy the image; deployment requires selecting a hosting platform and supplying its environment-specific configuration.

## Deploy to Azure Container Apps

The Bicep template in `infra/azure` provisions an Azure Container Apps environment, a 30-day Log Analytics workspace, and the application with HTTPS ingress, health probes, and the production Spring profile. It expects a publicly pullable container image and a secured external MongoDB deployment; no cloud resources are created by local builds or CI.

1. Build and publish a GHCR image (replace the owner and tag), then make the package public so Container Apps can pull it without registry credentials:

   ```powershell
   docker build --platform linux/amd64 --tag ghcr.io/OWNER/ai-job-agent:TAG .
   docker push ghcr.io/OWNER/ai-job-agent:TAG
   ```

2. Copy the example parameters file to `infra/azure/parameters.json`, then set `containerImage` to the published image and `mongodbUri` to the production MongoDB TLS connection string. The copied file is excluded by `.gitignore`; keep it local and do not include credentials in source control.
3. Sign in with the Azure CLI, create a resource group, and deploy the template:

   ```powershell
   az login
   az group create --name rg-ai-job-agent --location eastus
   az deployment group create --resource-group rg-ai-job-agent --template-file infra/azure/main.bicep --parameters @infra/azure/parameters.json
   ```

The deployment returns the HTTPS application URL as `appUrl`. The template keeps one replica because scheduled automation currently uses an in-process lock; do not scale it out until the scheduler uses a distributed lock. The app expects the database to be reachable from the Container Apps environment. Optional email delivery remains disabled; configure SMTP credentials as Container Apps secrets before enabling it. This template has not been applied to a live Azure subscription.

