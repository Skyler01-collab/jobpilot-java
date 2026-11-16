# 

# \# JobPilot - AI Agent and Developer Instructions

# 

# \## 1. Project Overview

# 

# JobPilot is a local-first job search assistant built using Java 17

# and Spring Boot 3.3.5.

# 

# It helps users:

# \- Import resumes from PDF, DOCX and TXT files.

# \- Build and maintain an editable professional profile.

# \- Collect job listings from supported job sources.

# \- Score jobs against user skills and preferences.

# \- Generate tailored resumes and cover letters.

# \- Manage saved application answers.

# \- Connect a browser extension for job-page assistance.

# \- Generate optional AI-assisted answers for application questions.

# 

# The user must review generated application content and manually

# submit job applications. The extension must never press the

# final application Submit button.

# 

# \## 2. Technology Stack

# 

# \- Java 17

# \- Spring Boot 3.3.5

# \- Maven

# \- Spring Web

# \- Spring Data JPA

# \- H2 database

# \- Lombok

# \- Apache PDFBox 3.0.3

# \- Apache POI 5.3.0

# \- OpenPDF 1.3.30

# \- HTML, CSS and vanilla JavaScript

# \- Chrome Extension Manifest V3

# \- Optional Anthropic API integration

# 

# Always check pom.xml before suggesting dependency or version changes.

# 

# \## 3. Project Architecture

# 

# Base Java package: com.jobpilot

# 

# \### model/

# Application models such as Profile and TailoredResume.

# 

# \### entity/

# JPA entities:

# \- Job

# \- ProfileEntity

# \- SavedAnswer

# 

# \### repo/

# Spring Data JPA repository interfaces.

# 

# \### service/

# Business logic, including:

# \- AiService

# \- JobService

# \- JobSourceClient

# \- MatchService

# \- ProfileService

# \- ResumeImportService

# \- ResumeRenderer

# \- SettingsService

# \- SkillTaxonomy

# \- TailorService

# 

# \### web/

# HTTP controllers and web infrastructure:

# \- AiController

# \- AnswerController

# \- JobController

# \- ProfileController

# \- SettingsController

# \- ApiExceptionHandler

# \- ApiHeaderFilter

# \- WebConfig

# \- ExtensionDownloadController

# \- ExtController

# 

# \### src/main/resources/static/

# Frontend HTML, CSS and JavaScript.

# 

# \### src/main/resources/extension/

# Browser extension resources and scripts.

# 

# \## 4. Application Architecture

# 

# Follow this general dependency direction:

# 

# Frontend / Browser Extension

# &#x20;       |

# &#x20;       v

# Web Controllers

# &#x20;       |

# &#x20;       v

# Service Layer

# &#x20;       |

# &#x20;       v

# Repository Layer

# &#x20;       |

# &#x20;       v

# H2 Database

# 

# External job providers and optional AI services

# are accessed through the appropriate service layer.

# 

# Do not move business logic into controllers.

# 

# \## 5. Core Business Rules

# 

# \### Resume Import

# \- Supported formats: PDF, DOCX and TXT.

# \- Imported information must remain editable.

# \- Preserve existing profile information according to

# &#x20; the selected merge or replace mode.

# 

# \### Job Matching

# \- Matching scores range from 0 to 100.

# \- Documented weighting:

# &#x20; - Skills: 55

# &#x20; - Job title: 20

# &#x20; - Experience: 15

# &#x20; - Location: 10

# \- Do not invent missing skills or experience.

# \- Preserve missing-skill information.

# 

# \### Resume Tailoring

# \- Only select, organize and reorder information

# &#x20; already present in the user's profile.

# \- Never fabricate qualifications, experience,

# &#x20; projects or technical skills.

# \- Keep missing skills visible as gaps.

# 

# \### Browser Extension

# \- Follow Manifest V3 conventions.

# \- Preserve the manual final-submit requirement.

# \- Never automatically submit job applications.

# \- Do not fill or store passwords, identity numbers

# &#x20; or sensitive personal questions.

# 

# \### Saved Answers

# \- Respect the existing application rules for

# &#x20; saving and reusing answers.

# \- Do not introduce storage of sensitive fields.

# 

# \## 6. Security and Privacy

# 

# \- Keep the application local-first.

# \- Preserve the loopback-only server binding.

# \- Preserve the X-JobPilot protection for state-changing

# &#x20; API requests.

# \- Preserve existing CORS restrictions.

# \- Never hardcode API keys, tokens or credentials.

# \- Use environment variables for external API keys.

# \- Never commit personal resumes, generated documents,

# &#x20; database files or local application data.

# \- Do not weaken security checks to make integrations work.

# 

# \## 7. Coding Standards

# 

# \- Follow Java naming conventions.

# \- Use meaningful class, method and variable names.

# \- Keep controllers focused on HTTP concerns.

# \- Keep business logic inside services.

# \- Use constructor-based dependency injection where appropriate.

# \- Follow REST API conventions.

# \- Handle errors consistently.

# \- Avoid unnecessary dependencies.

# \- Prefer readable and maintainable implementations.

# \- Follow the existing code style.

# 

# \## 8. Build and Run

# 

# Project build:

# mvn clean package

# 

# Run application:

# mvn spring-boot:run

# 

# Default application URL:

# http://127.0.0.1:8765

# 

# Check the actual application.properties before changing

# ports, database settings or application configuration.

# 

# \## 9. Testing Requirements

# 

# Before changing existing functionality:

# 

# 1\. Inspect the relevant controller.

# 2\. Trace the service implementation.

# 3\. Inspect related repository and entity classes.

# 4\. Understand existing behavior.

# 5\. Identify affected frontend and extension code.

# 6\. Add or update relevant tests.

# 7\. Run Maven tests and build checks.

# 

# Never claim tests passed unless they were actually executed.

# 

# \## 10. Documentation Requirements

# 

# When adding or changing features:

# 

# \- Update README.md when user-facing behavior changes.

# \- Update docs/architecture.md for architecture changes.

# \- Update docs/api-reference.md for API changes.

# \- Update docs/data-flow.md for workflow changes.

# \- Keep examples consistent with actual implementation.

# \- Document configuration and security implications.

# 

# \## 11. AI Agent Rules

# 

# Before modifying code:

# 

# \- Inspect existing implementations.

# \- Search for existing reusable functionality.

# \- Avoid duplicating services or business logic.

# \- Check dependencies and configuration.

# \- Preserve backwards compatibility where possible.

# \- Explain significant design decisions.

# 

# Never assume a class, method, endpoint or dependency exists

# without checking the repository.

# 

# If documentation conflicts with implementation,

# trust the implementation and update the documentation.

# 

# Do not make unrelated changes.

# 

