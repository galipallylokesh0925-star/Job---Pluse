/**
 * AI Job Application Agent - Dashboard Frontend Logic
 * Supports Phase 1 Diagnostics, Phase 2 Candidate Profile & Resume Engine,
 * Phase 3 Multi-Source Job Discovery Connectors, Phase 4 AI Match & Scoring Engine,
 * Phase 5 evidence-grounded application preparation, Phase 6 authorized submissions,
 * and Phase 7 opt-in scheduled discovery and matching.
 */

// Global State
let currentSkills = [];
let activeProfileData = null;
let currentJobPage = 0;
let currentJobSize = 12;
let currentTotalPages = 0;
let searchDebounceTimer = null;
let currentMatchPage = 0;
let currentMatchSize = 12;
let currentMatchTotalPages = 0;
let matchSearchDebounceTimer = null;
let preparedApplicationJobId = null;

document.addEventListener('DOMContentLoaded', () => {
    // Phase 1: Initialize System Health Telemetry
    checkSystemHealth();

    const refreshBtn = document.getElementById('refreshHealthBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', (e) => {
            e.preventDefault();
            checkSystemHealth();
        });
    }

    // Phase 2: Initialize Candidate Profile & Resume Engine
    initProfileEngine();

    // Phase 3: Initialize Job Discovery & Listings
    initJobDiscoveryEngine();

    // Phase 4: Initialize AI Match & Scoring Engine
    initMatchingEngine();

    // Phase 5: Initialize Application Preparation
    initApplicationPreparation();

    // Phase 7: Initialize opt-in background automation controls
    initAutomationDashboard();
});

/* ==========================================================================
   PHASE 1: HEALTH & DIAGNOSTICS TELEMETRY
   ========================================================================== */

async function checkSystemHealth() {
    const statusPill = document.getElementById('systemStatusPill');
    const statusText = document.getElementById('systemStatusText');
    const pulseDot = document.getElementById('pulseDot');
    const dbStatusBadge = document.getElementById('dbStatusBadge');
    const javaVersionBadge = document.getElementById('javaVersionBadge');
    const rawJsonBlock = document.getElementById('rawJsonBlock');
    const lastCheckedTime = document.getElementById('lastCheckedTime');

    if (statusText) statusText.textContent = 'Checking...';
    const startTime = performance.now();

    try {
        const response = await fetch('/api/v1/health', {
            method: 'GET',
            headers: { 'Accept': 'application/json' }
        });

        const latency = Math.round(performance.now() - startTime);
        const data = await response.json();

        if (response.ok && data.status === 'UP') {
            if (pulseDot) pulseDot.className = 'pulse-indicator online';
            if (statusText) statusText.textContent = `Online • ${latency}ms`;
            if (statusPill) statusPill.className = 'badge bg-success-subtle text-success border border-success-subtle px-3 py-2';
            if (dbStatusBadge) {
                dbStatusBadge.textContent = data.database || 'Connected';
                dbStatusBadge.className = 'badge bg-success-subtle text-success';
            }
        } else {
            if (pulseDot) pulseDot.className = 'pulse-indicator offline';
            if (statusText) statusText.textContent = data.status || 'Degraded';
            if (statusPill) statusPill.className = 'badge bg-warning-subtle text-warning border border-warning-subtle px-3 py-2';
            if (dbStatusBadge) {
                dbStatusBadge.textContent = data.database || 'Disconnected';
                dbStatusBadge.className = 'badge bg-danger-subtle text-danger';
            }
        }

        if (javaVersionBadge && data.systemInfo) {
            javaVersionBadge.textContent = `Java ${data.systemInfo.javaVersion || '21'}`;
        }

        if (rawJsonBlock) {
            rawJsonBlock.textContent = JSON.stringify(data, null, 2);
        }

        if (lastCheckedTime) {
            lastCheckedTime.textContent = new Date().toLocaleTimeString();
        }

    } catch (error) {
        console.error('Error contacting health endpoint:', error);
        if (pulseDot) pulseDot.className = 'pulse-indicator offline';
        if (statusText) statusText.textContent = 'Offline / Connecting';
        if (statusPill) statusPill.className = 'badge bg-danger-subtle text-danger border border-danger-subtle px-3 py-2';
        if (rawJsonBlock) {
            rawJsonBlock.textContent = JSON.stringify({
                status: 'UNREACHABLE',
                error: error.message,
                hint: 'Ensure Spring Boot server is started on port 8080.'
            }, null, 2);
        }
    }
}

/* ==========================================================================
   PHASE 2: CANDIDATE PROFILE & RESUME ENGINE
   ========================================================================== */

function initProfileEngine() {
    loadActiveProfile();

    const dropzoneArea = document.getElementById('dropzoneArea');
    const fileInput = document.getElementById('resumeFileInput');
    const browseBtn = document.getElementById('browseFileBtn');

    if (browseBtn && fileInput) {
        browseBtn.addEventListener('click', () => fileInput.click());
    }

    if (fileInput) {
        fileInput.addEventListener('change', (e) => {
            if (e.target.files && e.target.files.length > 0) {
                uploadResumeFile(e.target.files[0]);
            }
        });
    }

    if (dropzoneArea) {
        ['dragenter', 'dragover'].forEach(eventName => {
            dropzoneArea.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzoneArea.classList.add('dragover');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropzoneArea.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzoneArea.classList.remove('dragover');
            }, false);
        });

        dropzoneArea.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            if (files && files.length > 0) {
                uploadResumeFile(files[0]);
            }
        });
    }

    const addSkillBtn = document.getElementById('addSkillBtn');
    const newSkillInput = document.getElementById('newSkillInput');

    if (addSkillBtn && newSkillInput) {
        addSkillBtn.addEventListener('click', () => handleAddSkill());
        newSkillInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                handleAddSkill();
            }
        });
    }

    document.querySelectorAll('.quick-skill-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const skill = btn.getAttribute('data-skill');
            if (skill) addSkill(skill);
        });
    });

    const saveProfileBtn = document.getElementById('saveProfileBtn');
    if (saveProfileBtn) {
        saveProfileBtn.addEventListener('click', (e) => {
            e.preventDefault();
            saveCandidateProfile();
        });
    }

    const viewRawTextBtn = document.getElementById('viewRawTextBtn');
    if (viewRawTextBtn) {
        viewRawTextBtn.addEventListener('click', (e) => {
            e.preventDefault();
            openRawTextModal();
        });
    }

    const resetProfileBtn = document.getElementById('resetProfileBtn');
    if (resetProfileBtn) {
        resetProfileBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (confirm('Are you sure you want to reset candidate profile and clear uploaded resume data?')) {
                resetCandidateProfile();
            }
        });
    }
}

async function loadActiveProfile() {
    try {
        const response = await fetch('/api/v1/profile');
        if (!response.ok) return;

        const profile = await response.json();
        activeProfileData = profile;
        renderProfileData(profile);
    } catch (err) {
        console.error('Error fetching candidate profile:', err);
    }
}

function renderProfileData(p) {
    if (!p) return;

    setValue('profileFullName', p.fullName || '');
    setValue('profileEmail', p.email || '');
    setValue('profilePhone', p.phone || '');
    setValue('profileLocation', p.location || '');
    setValue('profileLinkedIn', p.linkedInUrl || '');
    setValue('profileGithub', p.githubUrl || '');
    setValue('profilePortfolio', p.portfolioUrl || '');
    setValue('profileSummary', p.summary || '');

    currentSkills = Array.isArray(p.skills) ? [...p.skills] : [];
    renderSkillsCloud();

    if (p.preferences) {
        const pref = p.preferences;
        if (pref.targetRoles && pref.targetRoles.length > 0) {
            setValue('prefTargetRoles', pref.targetRoles.join(', '));
            renderHeroRoles(pref.targetRoles);
        }
        if (pref.preferredLocations && pref.preferredLocations.length > 0) {
            setValue('prefLocations', pref.preferredLocations.join(', '));
        }

        const workModes = pref.preferredWorkModes || [];
        setChecked('workModeRemote', workModes.includes('REMOTE'));
        setChecked('workModeHybrid', workModes.includes('HYBRID'));
        setChecked('workModeOnSite', workModes.includes('ON_SITE'));

        if (pref.maxDailyApplications) {
            setValue('prefMaxDaily', pref.maxDailyApplications);
        }
        if (pref.expectedSalaryMin) {
            setValue('prefMinSalary', pref.expectedSalaryMin);
        }
        setChecked('prefImmediateJoiner', pref.immediateJoiner !== false);
    }

    const docHasResumeBadge = document.getElementById('docHasResumeBadge');
    if (p.hasResume && p.resumeMetadata) {
        const meta = p.resumeMetadata;
        setText('docFileName', meta.fileName || 'Uploaded Resume');
        setText('docParserEngine', meta.parserEngine || 'PDFBox / POI');
        setText('docFileSize', meta.sizeBytes ? `${Math.round(meta.sizeBytes / 1024)} KB` : 'N/A');
        setText('docCharCount', `${meta.extractedCharacterCount || 0} characters`);
        setText('docParsedAt', meta.parsedAt ? new Date(meta.parsedAt).toLocaleString() : 'Recently');

        if (docHasResumeBadge) {
            docHasResumeBadge.textContent = 'Resume Parsed & Active';
            docHasResumeBadge.className = 'badge bg-success-subtle text-success';
        }
    } else {
        if (docHasResumeBadge) {
            docHasResumeBadge.textContent = 'No File Uploaded';
            docHasResumeBadge.className = 'badge bg-secondary-subtle text-secondary';
        }
    }
}

async function uploadResumeFile(file) {
    if (!file) return;

    const progressContainer = document.getElementById('uploadProgressContainer');
    const resultAlert = document.getElementById('uploadResultAlert');
    const resultMsg = document.getElementById('uploadResultMessage');
    const resultIcon = document.getElementById('uploadResultIcon');

    if (progressContainer) progressContainer.classList.remove('d-none');
    if (resultAlert) resultAlert.classList.add('d-none');

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/v1/profile/resume/upload', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();
        if (progressContainer) progressContainer.classList.add('d-none');

        if (response.ok && data.success) {
            if (resultAlert) {
                resultAlert.className = 'alert alert-success alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-check-circle-fill text-success fs-5';
            if (resultMsg) {
                resultMsg.innerHTML = `<strong>Success!</strong> ${data.message} <br>
                    <span class="text-white-50">Extracted ${data.extractedSkillsCount} skills and ${data.extractedCharacterCount} characters using ${data.parserEngine}.</span>`;
            }

            if (data.profile) {
                activeProfileData = data.profile;
                renderProfileData(data.profile);
            }
        } else {
            if (resultAlert) {
                resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-exclamation-triangle-fill text-danger fs-5';
            if (resultMsg) {
                resultMsg.textContent = data.message || 'Failed to parse resume document.';
            }
        }
    } catch (err) {
        if (progressContainer) progressContainer.classList.add('d-none');
        if (resultAlert) {
            resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
            resultAlert.classList.remove('d-none');
        }
        if (resultMsg) {
            resultMsg.textContent = 'Network or server error during upload: ' + err.message;
        }
    }
}

function handleAddSkill() {
    const input = document.getElementById('newSkillInput');
    if (!input) return;
    const skill = input.value.trim();
    if (skill) {
        addSkill(skill);
        input.value = '';
    }
}

function addSkill(skillName) {
    if (!skillName) return;
    const exists = currentSkills.some(s => s.toLowerCase() === skillName.toLowerCase());
    if (!exists) {
        currentSkills.push(skillName);
        renderSkillsCloud();
    }
}

function removeSkill(skillName) {
    currentSkills = currentSkills.filter(s => s !== skillName);
    renderSkillsCloud();
}

function renderSkillsCloud() {
    const cloud = document.getElementById('skillsCloud');
    const badge = document.getElementById('skillCountBadge');
    if (!cloud) return;

    if (badge) badge.textContent = currentSkills.length;

    if (currentSkills.length === 0) {
        cloud.innerHTML = '<span class="text-dim small">No skills parsed yet. Upload a resume or add skills below.</span>';
        return;
    }

    cloud.innerHTML = '';
    currentSkills.forEach(skill => {
        const chip = document.createElement('span');
        chip.className = 'skill-chip';
        chip.innerHTML = `
            <span>${escapeHtml(skill)}</span>
            <i class="bi bi-x-circle-fill btn-close-skill" title="Remove skill"></i>
        `;
        chip.querySelector('.btn-close-skill').addEventListener('click', () => removeSkill(skill));
        cloud.appendChild(chip);
    });
}

async function saveCandidateProfile() {
    const saveBtn = document.getElementById('saveProfileBtn');
    const originalText = saveBtn ? saveBtn.innerHTML : '';
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Saving...';
    }

    const targetRoles = (getValue('prefTargetRoles') || '')
        .split(',')
        .map(r => r.trim())
        .filter(r => r.length > 0);

    const locations = (getValue('prefLocations') || '')
        .split(',')
        .map(l => l.trim())
        .filter(l => l.length > 0);

    const preferredWorkModes = [];
    if (isChecked('workModeRemote')) preferredWorkModes.push('REMOTE');
    if (isChecked('workModeHybrid')) preferredWorkModes.push('HYBRID');
    if (isChecked('workModeOnSite')) preferredWorkModes.push('ON_SITE');

    const maxDaily = parseInt(getValue('prefMaxDaily')) || 20;
    const minSalary = parseFloat(getValue('prefMinSalary')) || null;
    const immediateJoiner = isChecked('prefImmediateJoiner');

    const payload = {
        fullName: getValue('profileFullName'),
        email: getValue('profileEmail'),
        phone: getValue('profilePhone'),
        location: getValue('profileLocation'),
        linkedInUrl: getValue('profileLinkedIn'),
        githubUrl: getValue('profileGithub'),
        portfolioUrl: getValue('profilePortfolio'),
        summary: getValue('profileSummary'),
        skills: currentSkills,
        education: activeProfileData ? activeProfileData.education : [],
        experience: activeProfileData ? activeProfileData.experience : [],
        projects: activeProfileData ? activeProfileData.projects : [],
        certifications: activeProfileData ? activeProfileData.certifications : [],
        preferences: {
            targetRoles: targetRoles,
            preferredWorkModes: preferredWorkModes,
            preferredLocations: locations,
            experienceLevel: 'ENTRY_LEVEL_FRESHER',
            employmentTypes: ['INTERNSHIP', 'FULL_TIME'],
            maxDailyApplications: maxDaily,
            expectedSalaryMin: minSalary,
            immediateJoiner: immediateJoiner
        }
    };

    try {
        const response = await fetch('/api/v1/profile', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = '<i class="bi bi-check-lg me-1"></i> Saved Successfully!';
            setTimeout(() => { saveBtn.innerHTML = originalText; }, 2500);
        }

        if (response.ok) {
            activeProfileData = data;
            renderProfileData(data);
        } else {
            alert('Failed to save profile: ' + (data.message || 'Unknown error'));
        }
    } catch (err) {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalText;
        }
        alert('Network error while saving profile: ' + err.message);
    }
}

async function openRawTextModal() {
    const content = document.getElementById('rawResumeTextContent');
    if (content) content.textContent = 'Loading raw text...';

    const modalEl = document.getElementById('rawTextModal');
    if (!modalEl) return;
    const modal = new bootstrap.Modal(modalEl);
    modal.show();

    try {
        const response = await fetch('/api/v1/profile/raw-text');
        const data = await response.json();
        if (content) {
            content.textContent = data.rawResumeText || 'No text extracted yet.';
        }
    } catch (err) {
        if (content) content.textContent = 'Error loading raw text: ' + err.message;
    }
}

async function resetCandidateProfile() {
    try {
        const response = await fetch('/api/v1/profile', { method: 'DELETE' });
        if (response.ok) {
            currentSkills = [];
            loadActiveProfile();
            alert('Candidate profile reset successfully.');
        }
    } catch (err) {
        alert('Failed to reset profile: ' + err.message);
    }
}

/* ==========================================================================
   PHASE 3: MULTI-SOURCE JOB DISCOVERY & LISTINGS ENGINE
   ========================================================================== */

function initJobDiscoveryEngine() {
    // Initial fetch of job stats and listings
    fetchJobStats();
    fetchJobListings(0);
    loadSavedPortalLinks();

    // Scan & Discover Button
    const scanBtn = document.getElementById('scanJobsBtn');
    if (scanBtn) {
        scanBtn.addEventListener('click', (e) => {
            e.preventDefault();
            triggerJobScan();
        });
    }

    const importForm = document.getElementById('jobAlertImportForm');
    if (importForm) {
        importForm.addEventListener('submit', (event) => {
            event.preventDefault();
            importJobAlert();
        });
    }

    const scanPortalLinksBtn = document.getElementById('scanPortalLinksBtn');
    if (scanPortalLinksBtn) {
        scanPortalLinksBtn.addEventListener('click', scanPortalLinks);
    }
    document.getElementById('clearSavedPortalLinksBtn')?.addEventListener('click', clearSavedPortalLinks);

    // Clear Jobs Button
    const clearJobsBtn = document.getElementById('clearJobsDbBtn');
    if (clearJobsBtn) {
        clearJobsBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (confirm('Are you sure you want to delete all discovered job postings from MongoDB?')) {
                clearAllDiscoveredJobs();
            }
        });
    }

    // Keyword Search with 300ms Debounce
    const searchInput = document.getElementById('jobSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            clearTimeout(searchDebounceTimer);
            searchDebounceTimer = setTimeout(() => {
                currentJobPage = 0;
                fetchJobListings(0);
            }, 300);
        });
    }

    // Filter Select Dropdowns
    const workModeSelect = document.getElementById('filterWorkModeSelect');
    if (workModeSelect) {
        workModeSelect.addEventListener('change', () => {
            currentJobPage = 0;
            fetchJobListings(0);
        });
    }

    const sourceSelect = document.getElementById('filterSourceSelect');
    if (sourceSelect) {
        sourceSelect.addEventListener('change', () => {
            currentJobPage = 0;
            fetchJobListings(0);
        });
    }

    // Reset Filters Button
    const resetFiltersBtn = document.getElementById('resetFiltersBtn');
    if (resetFiltersBtn) {
        resetFiltersBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (searchInput) searchInput.value = '';
            if (workModeSelect) workModeSelect.value = 'ALL';
            if (sourceSelect) sourceSelect.value = 'ALL';
            currentJobPage = 0;
            fetchJobListings(0);
        });
    }

    // Pagination Buttons
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');

    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            if (currentJobPage > 0) {
                fetchJobListings(currentJobPage - 1);
            }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            if (currentJobPage < currentTotalPages - 1) {
                fetchJobListings(currentJobPage + 1);
            }
        });
    }
}

async function scanPortalLinks() {
    const input = document.getElementById('portalLinksInput');
    const button = document.getElementById('scanPortalLinksBtn');
    const result = document.getElementById('portalLinksResult');
    if (!input || !button || !result) return;

    const portalUrls = input.value.split(/\r?\n/).map(url => url.trim()).filter(Boolean);
    if (portalUrls.length === 0 || portalUrls.length > 20) {
        result.textContent = 'Enter between 1 and 20 portal links, one per line.';
        return;
    }

    button.disabled = true;
    result.textContent = 'Scanning public job feeds...';
    try {
        const configResponse = await fetch('/api/v1/automation/portal-links', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ portalUrls })
        });
        const config = await configResponse.json();
        if (!configResponse.ok) {
            throw new Error(config.message || `Could not save portal links (${configResponse.status}).`);
        }

        const response = await fetch('/api/v1/jobs/portal-links/discover', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ portalUrls })
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || `Portal scan failed (${response.status}).`);
        }

        let summary = `Saved ${config.savedPortalUrls.length} supported board(s) for scheduled scans. Scanned ${data.totalFetched} jobs; added ${data.newJobsSaved} new and skipped ${data.duplicatesSkipped} duplicates.`;
        if (config.unsupportedPortalUrls.length > 0) {
            summary += ` ${config.unsupportedPortalUrls.length} unsupported link(s) were not saved.`;
        }
        for (const item of data.results || []) {
            summary += `\n${item.source} — ${item.status}: ${item.message}`;
        }
        if (data.newJobsSaved > 0) {
            try {
                const matchResponse = await fetch('/api/v1/matches/evaluate-all', {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });
                const match = await matchResponse.json();
                if (matchResponse.ok && match.success) {
                    summary += `\nProfile matching complete: ${match.strongMatchesCount} strong and ${match.moderateMatchesCount} moderate matches. Review matches and confirm each application yourself.`;
                    fetchMatchStats();
                    fetchMatchListings(0);
                } else {
                    summary += `\nJobs were added, but matching needs attention: ${match.message || 'Check your candidate profile.'}`;
                }
            } catch (error) {
                summary += `\nJobs were added, but matching failed: ${error.message}`;
            }
        }
        result.textContent = summary;
        fetchJobStats();
        fetchJobListings(0);
    } catch (error) {
        result.textContent = error.message || 'Could not scan the supplied portal links.';
    } finally {
        button.disabled = false;
    }
}

async function loadSavedPortalLinks() {
    const input = document.getElementById('portalLinksInput');
    const result = document.getElementById('portalLinksResult');
    if (!input || input.value.trim()) return;
    try {
        const response = await fetch('/api/v1/automation/portal-links');
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Could not load saved portal links (${response.status}).`);
        input.value = (data.savedPortalUrls || []).join('\n');
    } catch (error) {
        if (result) result.textContent = `Saved portal links could not be loaded: ${error.message}`;
    }
}

async function clearSavedPortalLinks() {
    const input = document.getElementById('portalLinksInput');
    const button = document.getElementById('clearSavedPortalLinksBtn');
    const result = document.getElementById('portalLinksResult');
    if (!input || !button || !result) return;

    button.disabled = true;
    try {
        const response = await fetch('/api/v1/automation/portal-links', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ portalUrls: [] })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Could not clear saved links (${response.status}).`);
        input.value = '';
        result.textContent = 'Saved portal links cleared. Scheduled scans will no longer poll these boards.';
    } catch (error) {
        result.textContent = error.message;
    } finally {
        button.disabled = false;
    }
}

/**
 * Triggers discovery scan across selected connectors via POST /api/v1/jobs/discover
 */
async function triggerJobScan() {
    const scanBtn = document.getElementById('scanJobsBtn');
    const originalHtml = scanBtn ? scanBtn.innerHTML : '';
    const progressContainer = document.getElementById('discoveryProgressContainer');
    const resultAlert = document.getElementById('discoveryResultAlert');
    const resultMsg = document.getElementById('discoveryResultMessage');
    const resultIcon = document.getElementById('discoveryResultIcon');

    // Collect active connector selection
    const selectedConnectors = [];
    if (isChecked('connectorRemoteOk')) selectedConnectors.push('REMOTE_OK');
    if (isChecked('connectorArbeitnow')) selectedConnectors.push('ARBEITNOW');
    if (isChecked('connectorEnterprise')) selectedConnectors.push('ENTERPRISE_PORTAL');
    if (isChecked('connectorGreenhouse')) selectedConnectors.push('GREENHOUSE');
    if (isChecked('connectorLever')) selectedConnectors.push('LEVER');
    if (isChecked('connectorRss')) selectedConnectors.push('RSS_FEED');

    if (scanBtn) {
        scanBtn.disabled = true;
        scanBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Scanning Feeds...';
    }
    if (progressContainer) progressContainer.classList.remove('d-none');
    if (resultAlert) resultAlert.classList.add('d-none');

    try {
        const response = await fetch('/api/v1/jobs/discover', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(selectedConnectors)
        });

        const data = await response.json();

        if (progressContainer) progressContainer.classList.add('d-none');
        if (scanBtn) {
            scanBtn.disabled = false;
            scanBtn.innerHTML = originalHtml;
        }

        if (response.ok && data.success) {
            let rankingSummary = 'Ranking not run; check your candidate profile.';
            try {
                const matchResponse = await fetch('/api/v1/matches/evaluate-all', {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });
                const match = await matchResponse.json();
                if (matchResponse.ok && match.success) {
                    rankingSummary = `Ranked ${match.totalJobsEvaluated} jobs: ${match.strongMatchesCount} strong matches, ${match.moderateMatchesCount} moderate matches.`;
                    fetchMatchStats();
                    fetchMatchListings(0);
                } else {
                    rankingSummary = `Jobs were collected, but ranking is unavailable: ${match.message || 'Set up your candidate profile first.'}`;
                }
            } catch (matchError) {
                rankingSummary = `Jobs were collected, but ranking failed: ${matchError.message}`;
            }
            if (resultAlert) {
                resultAlert.className = 'alert alert-success alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-check-circle-fill text-success fs-5';
            if (resultMsg) {
                resultMsg.textContent = `Scan complete (${data.durationMs}ms). ${data.message} Breakdown: ${formatSourceBreakdown(data.sourceBreakdown)} ${rankingSummary}`;
            }

            // Refresh UI
            fetchJobStats();
            fetchJobListings(0);
        } else {
            if (resultAlert) {
                resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-exclamation-triangle-fill text-danger fs-5';
            if (resultMsg) resultMsg.textContent = data.message || 'Discovery scan failed.';
        }
    } catch (err) {
        if (progressContainer) progressContainer.classList.add('d-none');
        if (scanBtn) {
            scanBtn.disabled = false;
            scanBtn.innerHTML = originalHtml;
        }
        if (resultAlert) {
            resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
            resultAlert.classList.remove('d-none');
        }
        if (resultMsg) resultMsg.textContent = 'Network or server error during scan: ' + err.message;
    }
}

async function importJobAlert() {
    const form = document.getElementById('jobAlertImportForm');
    const submitButton = document.getElementById('importJobAlertBtn');
    const result = document.getElementById('jobAlertImportResult');
    if (!form || !submitButton || !result) return;

    const originalButtonText = submitButton.innerHTML;
    submitButton.disabled = true;
    submitButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Importing...';
    result.textContent = '';
    result.classList.remove('text-danger');

    const request = {
        source: getValue('jobAlertSource'),
        title: getValue('jobAlertTitle'),
        company: getValue('jobAlertCompany'),
        applyUrl: getValue('jobAlertApplyUrl'),
        location: getValue('jobAlertLocation'),
        workMode: getValue('jobAlertWorkMode'),
        description: getValue('jobAlertDescription')
    };

    try {
        const response = await fetch('/api/v1/jobs/alerts/import', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(request)
        });
        const data = await response.json();
        if (!response.ok || !data.success) {
            throw new Error(data.message || `Job alert import failed (${response.status}).`);
        }

        fetchJobStats();
        fetchJobListings(0);
        const job = data.job;
        if (job && job.id) {
            try {
                const matchResponse = await fetch(`/api/v1/matches/evaluate/${encodeURIComponent(job.id)}`, {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });
                const match = await matchResponse.json();
                if (!matchResponse.ok) {
                    result.textContent = `${data.message} Ranking unavailable: ${match.message || 'Set up your candidate profile first.'}`;
                } else {
                    result.textContent = `${data.message} Match score: ${match.overallScore}%.`;
                    fetchMatchStats();
                    fetchMatchListings(0);
                }
            } catch (matchError) {
                result.textContent = `${data.message} Job was saved, but ranking failed: ${matchError.message}`;
            }
        } else {
            result.textContent = data.message;
        }

        if (!data.duplicate) {
            form.reset();
            setValue('jobAlertSource', request.source);
        }
    } catch (error) {
        result.textContent = error.message || 'Job alert could not be imported.';
        result.classList.add('text-danger');
    } finally {
        submitButton.disabled = false;
        submitButton.innerHTML = originalButtonText;
    }
}

/**
 * Fetches discovery stats from GET /api/v1/jobs/stats
 */
async function fetchJobStats() {
    try {
        const response = await fetch('/api/v1/jobs/stats');
        if (!response.ok) return;

        const stats = await response.json();
        setText('statsTotalJobs', stats.totalJobs || 0);
        setText('statsRemoteJobs', stats.remoteJobs || 0);
        setText('statsHybridJobs', stats.hybridJobs || 0);
        setText('statsOnSiteJobs', stats.onSiteJobs || 0);

        const heroTotal = document.getElementById('metricTotalJobsHero');
        if (heroTotal) heroTotal.textContent = stats.totalJobs || 0;
    } catch (err) {
        console.error('Error fetching job stats:', err);
    }
}

/**
 * Searches and paginates job postings from GET /api/v1/jobs
 */
async function fetchJobListings(page = 0) {
    currentJobPage = page;

    const keyword = getValue('jobSearchInput');
    const workMode = getValue('filterWorkModeSelect') || 'ALL';
    const source = getValue('filterSourceSelect') || 'ALL';

    const params = new URLSearchParams({
        page: page,
        size: currentJobSize
    });
    if (keyword) params.append('keyword', keyword);
    if (workMode && workMode !== 'ALL') params.append('workMode', workMode);
    if (source && source !== 'ALL') params.append('source', source);

    try {
        const response = await fetch(`/api/v1/jobs?${params.toString()}`);
        if (!response.ok) return;

        const data = await response.json();
        const jobs = data.content || [];
        const pageInfo = data.page || {};

        currentTotalPages = pageInfo.totalPages || 0;
        renderJobListings(jobs);
        renderPagination(pageInfo);
    } catch (err) {
        console.error('Error fetching job listings:', err);
    }
}

/**
 * Renders job listing cards into #jobListingsContainer
 */
function renderJobListings(jobs) {
    const container = document.getElementById('jobListingsContainer');
    if (!container) return;

    if (!jobs || jobs.length === 0) {
        container.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-briefcase text-secondary display-4"></i>
                <h5 class="fw-semibold mt-3 text-muted">No Job Postings Match Filters</h5>
                <p class="text-dim small">Try adjusting your search criteria or click <strong>"Scan & Discover Jobs"</strong>.</p>
            </div>
        `;
        return;
    }

    container.innerHTML = '';
    jobs.forEach(job => {
        const col = document.createElement('div');
        col.className = 'col-12 col-md-6 col-xl-4';

        const workModeClass = getWorkModeBadgeClass(job.workMode);
        const initials = getCompanyInitials(job.company);
        const skillsHtml = (job.requiredSkills || []).slice(0, 4).map(s =>
            `<span class="tag-badge" style="font-size:0.75rem; padding:0.15rem 0.5rem; margin-right:0.25rem;">${escapeHtml(s)}</span>`
        ).join('');

        col.innerHTML = `
            <div class="job-card h-100 d-flex flex-column justify-content-between">
                <div>
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div class="d-flex align-items-center gap-3">
                            <div class="company-avatar">${escapeHtml(initials)}</div>
                            <div>
                                <h6 class="fw-bold mb-0 text-truncate" style="max-width: 200px;" title="${escapeHtml(job.title)}">${escapeHtml(job.title)}</h6>
                                <span class="text-muted small">${escapeHtml(job.company || 'Enterprise')}</span>
                            </div>
                        </div>
                        <span class="badge ${workModeClass} px-2 py-1" style="font-size: 0.72rem;">${escapeHtml(job.workMode)}</span>
                    </div>

                    <div class="d-flex flex-wrap gap-2 text-dim small my-2">
                        <span><i class="bi bi-geo-alt me-1 text-info"></i>${escapeHtml(job.location || 'Remote')}</span>
                        <span><i class="bi bi-building me-1 text-primary"></i>${escapeHtml(job.employmentType || 'Full-time')}</span>
                    </div>

                    <p class="text-muted small mb-2 text-truncate-2" style="font-size: 0.825rem; max-height: 40px; overflow: hidden;">
                        ${escapeHtml(job.description ? job.description.substring(0, 140) + '...' : 'Direct employer opportunity.')}
                    </p>

                    <div class="mb-3">${skillsHtml}</div>
                </div>

                <div class="pt-3 border-top border-secondary-subtle d-flex justify-content-between align-items-center">
                    <span class="badge badge-source">${escapeHtml(job.source)}</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-xs btn-outline-info view-job-btn py-1 px-2" style="font-size: 0.8rem;" data-job-id="${escapeHtml(job.id)}">
                            <i class="bi bi-info-circle me-1"></i> Details
                        </button>
                        <a href="${escapeHtml(job.applyUrl || '#')}" target="_blank" class="btn btn-xs btn-primary py-1 px-2 d-flex align-items-center gap-1" style="font-size: 0.8rem;">
                            <span>Apply</span> <i class="bi bi-box-arrow-up-right" style="font-size: 0.7rem;"></i>
                        </a>
                    </div>
                </div>
            </div>
        `;

        col.querySelector('.view-job-btn').addEventListener('click', () => openJobDetailModal(job));
        container.appendChild(col);
    });
}

function openJobDetailModal(job) {
    if (!job) return;

    setText('modalJobTitle', job.title);
    setText('modalJobCompany', `${job.company} • ${job.location || 'Remote'} • Discovered ${new Date(job.discoveredAt).toLocaleDateString()}`);

    const badgesContainer = document.getElementById('modalJobBadges');
    if (badgesContainer) {
        badgesContainer.innerHTML = `
            <span class="badge ${getWorkModeBadgeClass(job.workMode)} px-2 py-1">${escapeHtml(job.workMode)}</span>
            <span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1">${escapeHtml(job.employmentType)}</span>
            <span class="badge badge-source px-2 py-1">${escapeHtml(job.source)}</span>
            ${job.salaryMin ? `<span class="badge bg-success-subtle text-success border border-success-subtle px-2 py-1">${job.salaryCurrency} ${job.salaryMin.toLocaleString()}</span>` : ''}
        `;
    }

    const skillsContainer = document.getElementById('modalJobSkills');
    if (skillsContainer) {
        skillsContainer.innerHTML = (job.requiredSkills || []).map(s =>
            `<span class="tag-badge">${escapeHtml(s)}</span>`
        ).join('') || '<span class="text-dim small">No specific skills listed.</span>';
    }

    const descContainer = document.getElementById('modalJobDescription');
    if (descContainer) {
        descContainer.textContent = job.description || 'No detailed description provided.';
    }

    const applyBtn = document.getElementById('modalJobApplyBtn');
    if (applyBtn) {
        applyBtn.href = job.applyUrl || '#';
    }

    const modalEl = document.getElementById('jobDetailModal');
    if (modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }
}

function renderPagination(pageInfo) {
    const paginationEl = document.getElementById('jobsPagination');
    const infoEl = document.getElementById('paginationInfo');
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');

    if (!paginationEl) return;

    const totalElements = pageInfo.totalElements || 0;
    const totalPages = pageInfo.totalPages || 0;
    const pageNumber = pageInfo.number || 0;
    const pageSize = pageInfo.size || currentJobSize;

    if (totalElements === 0) {
        paginationEl.classList.add('d-none');
        return;
    }

    paginationEl.classList.remove('d-none');
    const start = pageNumber * pageSize + 1;
    const end = Math.min((pageNumber + 1) * pageSize, totalElements);
    if (infoEl) infoEl.textContent = `Showing ${start}-${end} of ${totalElements} jobs (Page ${pageNumber + 1} of ${totalPages})`;

    if (prevBtn) prevBtn.disabled = pageNumber <= 0;
    if (nextBtn) nextBtn.disabled = pageNumber >= totalPages - 1;
}

async function clearAllDiscoveredJobs() {
    try {
        const response = await fetch('/api/v1/jobs', { method: 'DELETE' });
        if (response.ok) {
            fetchJobStats();
            fetchJobListings(0);
            alert('All discovered job postings cleared from MongoDB.');
        }
    } catch (err) {
        alert('Failed to clear jobs: ' + err.message);
    }
}

function getWorkModeBadgeClass(mode) {
    if (!mode) return 'badge-workmode-remote';
    switch (mode.toUpperCase()) {
        case 'REMOTE': return 'badge-workmode-remote';
        case 'HYBRID': return 'badge-workmode-hybrid';
        case 'ON_SITE': return 'badge-workmode-onsite';
        default: return 'badge-workmode-remote';
    }
}

function getCompanyInitials(company) {
    if (!company) return 'CO';
    const words = company.trim().split(/\s+/);
    if (words.length >= 2) {
        return (words[0][0] + words[1][0]).toUpperCase();
    }
    return company.substring(0, 2).toUpperCase();
}

function formatSourceBreakdown(breakdown) {
    if (!breakdown) return 'N/A';
    return Object.entries(breakdown).map(([k, v]) => `${k}: ${v}`).join(' • ');
}

function renderHeroRoles(roles) {
    const container = document.getElementById('heroPreferredRoles');
    if (!container || !roles || roles.length === 0) return;
    container.innerHTML = roles.map(role =>
        `<span class="tag-badge"><i class="bi bi-briefcase me-1"></i> ${escapeHtml(role)}</span>`
    ).join('');
}

// Utility Helpers
function getValue(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : '';
}

function setValue(id, val) {
    const el = document.getElementById(id);
    if (el) el.value = val;
}

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function isChecked(id) {
    const el = document.getElementById(id);
    return el ? el.checked : false;
}

function setChecked(id, checked) {
    const el = document.getElementById(id);
    if (el) el.checked = Boolean(checked);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g,
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}

/* ==========================================================================
   PHASE 4: AI RESUME MATCH & SCORING ENGINE
   ========================================================================== */

function initMatchingEngine() {
    // Load initial match stats and listings
    fetchMatchStats();
    fetchMatchListings(0);

    // Run Batch Match Button
    const runBatchBtn = document.getElementById('runBatchMatchBtn');
    if (runBatchBtn) {
        runBatchBtn.addEventListener('click', (e) => {
            e.preventDefault();
            triggerBatchMatch();
        });
    }

    // Clear Matches Button
    const clearMatchesBtn = document.getElementById('clearMatchesBtn');
    if (clearMatchesBtn) {
        clearMatchesBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (confirm('Are you sure you want to clear all AI match evaluations from MongoDB?')) {
                clearAllMatches();
            }
        });
    }

    // Keyword Search with 300ms Debounce
    const matchSearchInput = document.getElementById('matchSearchInput');
    if (matchSearchInput) {
        matchSearchInput.addEventListener('input', () => {
            clearTimeout(matchSearchDebounceTimer);
            matchSearchDebounceTimer = setTimeout(() => {
                currentMatchPage = 0;
                fetchMatchListings(0);
            }, 300);
        });
    }

    // Tier filter dropdown
    const tierSelect = document.getElementById('filterMatchTierSelect');
    if (tierSelect) {
        tierSelect.addEventListener('change', () => {
            currentMatchPage = 0;
            fetchMatchListings(0);
        });
    }

    // Hard Criteria Only toggle
    const hardCriteriaToggle = document.getElementById('filterHardCriteriaOnly');
    if (hardCriteriaToggle) {
        hardCriteriaToggle.addEventListener('change', () => {
            currentMatchPage = 0;
            fetchMatchListings(0);
        });
    }

    // Reset Match Filters Button
    const resetMatchFiltersBtn = document.getElementById('resetMatchFiltersBtn');
    if (resetMatchFiltersBtn) {
        resetMatchFiltersBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (matchSearchInput) matchSearchInput.value = '';
            if (tierSelect) tierSelect.value = 'ALL';
            if (hardCriteriaToggle) hardCriteriaToggle.checked = false;
            currentMatchPage = 0;
            fetchMatchListings(0);
        });
    }

    // Match Pagination Buttons
    const matchPrevBtn = document.getElementById('matchPrevPageBtn');
    const matchNextBtn = document.getElementById('matchNextPageBtn');

    if (matchPrevBtn) {
        matchPrevBtn.addEventListener('click', () => {
            if (currentMatchPage > 0) {
                fetchMatchListings(currentMatchPage - 1);
            }
        });
    }

    if (matchNextBtn) {
        matchNextBtn.addEventListener('click', () => {
            if (currentMatchPage < currentMatchTotalPages - 1) {
                fetchMatchListings(currentMatchPage + 1);
            }
        });
    }
}

/**
 * Triggers batch AI qualification matching via POST /api/v1/matches/evaluate-all
 */
async function triggerBatchMatch() {
    const runBtn = document.getElementById('runBatchMatchBtn');
    const originalHtml = runBtn ? runBtn.innerHTML : '';
    const progressContainer = document.getElementById('matchingProgressContainer');
    const resultAlert = document.getElementById('matchingResultAlert');
    const resultMsg = document.getElementById('matchingResultMessage');
    const resultIcon = document.getElementById('matchingResultIcon');

    if (runBtn) {
        runBtn.disabled = true;
        runBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Evaluating Jobs...';
    }
    if (progressContainer) progressContainer.classList.remove('d-none');
    if (resultAlert) resultAlert.classList.add('d-none');

    try {
        const response = await fetch('/api/v1/matches/evaluate-all', {
            method: 'POST',
            headers: { 'Accept': 'application/json' }
        });

        const data = await response.json();

        if (progressContainer) progressContainer.classList.add('d-none');
        if (runBtn) {
            runBtn.disabled = false;
            runBtn.innerHTML = originalHtml;
        }

        if (response.ok && data.success) {
            if (resultAlert) {
                resultAlert.className = 'alert alert-success alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-check-circle-fill text-success fs-5';
            if (resultMsg) {
                resultMsg.innerHTML = `<strong>Batch Match Complete (${data.durationMs}ms)!</strong> ${data.message} <br>
                    <span class="text-white-50">Strong: ${data.strongMatchesCount} &nbsp;•&nbsp; Moderate: ${data.moderateMatchesCount} &nbsp;•&nbsp; Weak: ${data.weakMatchesCount} &nbsp;•&nbsp; Avg Score: ${data.averageScore}%</span>`;
            }

            // Refresh match listings and stats
            fetchMatchStats();
            fetchMatchListings(0);
        } else {
            if (resultAlert) {
                resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
                resultAlert.classList.remove('d-none');
            }
            if (resultIcon) resultIcon.className = 'bi bi-exclamation-triangle-fill text-danger fs-5';
            if (resultMsg) resultMsg.textContent = data.message || 'Batch match evaluation failed. Ensure a candidate profile exists and jobs are discovered.';
        }
    } catch (err) {
        if (progressContainer) progressContainer.classList.add('d-none');
        if (runBtn) {
            runBtn.disabled = false;
            runBtn.innerHTML = originalHtml;
        }
        if (resultAlert) {
            resultAlert.className = 'alert alert-danger alert-dismissible fade show mt-3';
            resultAlert.classList.remove('d-none');
        }
        if (resultMsg) resultMsg.textContent = 'Network or server error during batch match: ' + err.message;
    }
}

/**
 * Fetches match distribution stats from GET /api/v1/matches/stats
 */
async function fetchMatchStats() {
    try {
        const response = await fetch('/api/v1/matches/stats');
        if (!response.ok) return;

        const stats = await response.json();
        setText('matchStatsTotal', stats.totalEvaluated || 0);
        setText('matchStatsStrong', stats.strongMatches || 0);
        setText('matchStatsModerate', stats.moderateMatches || 0);
        setText('matchStatsPassed', stats.totalEvaluated > 0
            ? Math.round((stats.hardCriteriaPassed / stats.totalEvaluated) * 100) + '%'
            : '0%');
    } catch (err) {
        console.error('Error fetching match stats:', err);
    }
}

/**
 * Fetches ranked match results from GET /api/v1/matches with filters and pagination
 */
async function fetchMatchListings(page = 0) {
    currentMatchPage = page;

    const keyword = getValue('matchSearchInput');
    const tier = getValue('filterMatchTierSelect') || 'ALL';
    const hardCriteriaOnly = isChecked('filterHardCriteriaOnly');

    const params = new URLSearchParams({
        page: page,
        size: currentMatchSize
    });
    if (keyword) params.append('keyword', keyword);
    if (tier && tier !== 'ALL') params.append('tier', tier);
    if (hardCriteriaOnly) params.append('hardCriteriaOnly', 'true');

    try {
        const response = await fetch(`/api/v1/matches?${params.toString()}`);
        if (!response.ok) return;

        const data = await response.json();
        const matches = data.content || [];
        const pageInfo = data.page || {};

        currentMatchTotalPages = pageInfo.totalPages || 0;
        renderMatchListings(matches);
        renderMatchPagination(pageInfo);
    } catch (err) {
        console.error('Error fetching match listings:', err);
    }
}

/**
 * Renders match evaluation cards into #matchListingsContainer
 */
function renderMatchListings(matches) {
    const container = document.getElementById('matchListingsContainer');
    if (!container) return;

    if (!matches || matches.length === 0) {
        container.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-stars text-secondary display-4"></i>
                <h5 class="fw-semibold mt-3 text-muted">No AI Match Evaluations Match Filters</h5>
                <p class="text-dim small">Try adjusting filters or click <strong>"Run AI Match on All Jobs"</strong> to evaluate discovered jobs.</p>
            </div>
        `;
        return;
    }

    container.innerHTML = '';
    matches.forEach(match => {
        const col = document.createElement('div');
        col.className = 'col-12 col-md-6 col-xl-4';

        const scoreClass = getScoreCircleClass(match.overallScore);
        const tierBadge = getTierBadge(match.recommendationTier, match.hardCriteriaMet);
        const workModeClass = getWorkModeBadgeClass(match.workMode);
        const initials = getCompanyInitials(match.company);

        const matchedSkillsHtml = (match.matchedSkills || []).slice(0, 3).map(s =>
            `<span class="pill-skill-matched"><i class="bi bi-check-lg" style="font-size: 0.65rem;"></i> ${escapeHtml(s)}</span>`
        ).join('');
        const missingSkillsHtml = (match.missingSkills || []).slice(0, 3).map(s =>
            `<span class="pill-skill-missing"><i class="bi bi-exclamation-sm" style="font-size: 0.65rem;"></i> ${escapeHtml(s)}</span>`
        ).join('');

        // Build sub-score bars HTML
        const subScoresHtml = buildSubScoreBars(match);

        col.innerHTML = `
            <div class="job-card h-100 d-flex flex-column justify-content-between" style="border-color: ${getTierBorderColor(match.recommendationTier, match.hardCriteriaMet)};">
                <div>
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <div class="d-flex align-items-center gap-3">
                            <div class="company-avatar">${escapeHtml(initials)}</div>
                            <div>
                                <h6 class="fw-bold mb-0 text-truncate" style="max-width: 170px;" title="${escapeHtml(match.jobTitle)}">${escapeHtml(match.jobTitle)}</h6>
                                <span class="text-muted small">${escapeHtml(match.company || 'Enterprise')}</span>
                            </div>
                        </div>
                        <div class="${scoreClass} score-circle">
                            <span>${match.overallScore}</span>
                            <span style="font-size: 0.55rem; font-weight: 600; opacity: 0.8;">%</span>
                        </div>
                    </div>

                    <div class="d-flex flex-wrap gap-2 mb-2">
                        ${tierBadge}
                        <span class="badge ${workModeClass} px-2 py-1" style="font-size: 0.72rem;">${escapeHtml(match.workMode || 'N/A')}</span>
                        ${!match.hardCriteriaMet ? '<span class="badge bg-danger-subtle text-danger border border-danger-subtle px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-shield-exclamation me-1"></i>Criteria Fail</span>' : ''}
                    </div>

                    ${subScoresHtml}

                    <div class="my-2">
                        ${matchedSkillsHtml}
                        ${missingSkillsHtml}
                        ${(match.bonusSkills || []).slice(0, 2).map(s => `<span class="pill-skill-bonus"><i class="bi bi-star-fill" style="font-size: 0.6rem;"></i> ${escapeHtml(s)}</span>`).join('')}
                    </div>

                    <p class="text-muted small mb-0" style="font-size: 0.8rem; max-height: 38px; overflow: hidden;">
                        ${escapeHtml(match.qualitativeSummary ? match.qualitativeSummary.substring(0, 120) + '...' : 'Qualification evaluation complete.')}
                    </p>
                </div>

                <div class="pt-3 border-top border-secondary-subtle d-flex justify-content-between align-items-center mt-2">
                    <span class="badge badge-source">${match.matchEngine || 'Deterministic'}</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-xs btn-outline-warning py-1 px-2 view-match-btn" style="font-size: 0.8rem;" data-match-id="${escapeHtml(match.id)}">
                            <i class="bi bi-bar-chart-line me-1"></i>Analysis
                        </button>
                        <a href="${escapeHtml(match.applyUrl || '#')}" target="_blank" class="btn btn-xs btn-primary py-1 px-2 d-flex align-items-center gap-1" style="font-size: 0.8rem;">
                            <span>Apply</span> <i class="bi bi-box-arrow-up-right" style="font-size: 0.7rem;"></i>
                        </a>
                    </div>
                </div>
            </div>
        `;

        col.querySelector('.view-match-btn').addEventListener('click', () => openMatchDetailModal(match));
        container.appendChild(col);
    });
}

/**
 * Opens the AI match detail modal with full scoring breakdown, skill gaps, and preparation tips.
 */
function openMatchDetailModal(match) {
    if (!match) return;

    // Ensure the modal exists; if not, inject it
    ensureMatchDetailModal();

    setText('matchModalTitle', match.jobTitle || 'Job Analysis');
    setText('matchModalCompany', `${match.company || 'Company'} • ${match.location || 'Remote'}`);

    const overallEl = document.getElementById('matchModalOverallScore');
    if (overallEl) {
        overallEl.textContent = match.overallScore + '%';
        overallEl.className = 'fw-black ' + getScoreTextClass(match.overallScore);
        overallEl.style.fontSize = '2.5rem';
    }

    const tierEl = document.getElementById('matchModalTier');
    if (tierEl) tierEl.innerHTML = getTierBadge(match.recommendationTier, match.hardCriteriaMet);

    // Hard criteria violations
    const violationsEl = document.getElementById('matchModalViolations');
    const violationsBlock = document.getElementById('matchModalViolationsBlock');
    if (violationsBlock) {
        if (!match.hardCriteriaMet && match.hardCriteriaViolations && match.hardCriteriaViolations.length > 0) {
            violationsBlock.classList.remove('d-none');
            if (violationsEl) violationsEl.innerHTML = match.hardCriteriaViolations.map(v =>
                `<li>${escapeHtml(v)}</li>`
            ).join('');
        } else {
            violationsBlock.classList.add('d-none');
        }
    }

    // Sub-score bars (detailed)
    const detailedScoresEl = document.getElementById('matchModalDetailedScores');
    if (detailedScoresEl) {
        detailedScoresEl.innerHTML = `
            <div class="mb-3">
                <div class="d-flex justify-content-between mb-1">
                    <span class="small text-muted">Skills Match <small class="text-dim">(50%)</small></span>
                    <span class="small fw-semibold" style="color: ${getScoreColor(match.skillScore)}">${match.skillScore}%</span>
                </div>
                <div class="subscore-bar-container">
                    <div class="subscore-bar-fill" style="width: ${match.skillScore}%; background: ${getScoreColor(match.skillScore)};"></div>
                </div>
            </div>
            <div class="mb-3">
                <div class="d-flex justify-content-between mb-1">
                    <span class="small text-muted">Experience Alignment <small class="text-dim">(25%)</small></span>
                    <span class="small fw-semibold" style="color: ${getScoreColor(match.experienceScore)}">${match.experienceScore}%</span>
                </div>
                <div class="subscore-bar-container">
                    <div class="subscore-bar-fill" style="width: ${match.experienceScore}%; background: ${getScoreColor(match.experienceScore)};"></div>
                </div>
            </div>
            <div class="mb-3">
                <div class="d-flex justify-content-between mb-1">
                    <span class="small text-muted">Education Fit <small class="text-dim">(15%)</small></span>
                    <span class="small fw-semibold" style="color: ${getScoreColor(match.educationScore)}">${match.educationScore}%</span>
                </div>
                <div class="subscore-bar-container">
                    <div class="subscore-bar-fill" style="width: ${match.educationScore}%; background: ${getScoreColor(match.educationScore)};"></div>
                </div>
            </div>
            <div class="mb-0">
                <div class="d-flex justify-content-between mb-1">
                    <span class="small text-muted">Preferences Alignment <small class="text-dim">(10%)</small></span>
                    <span class="small fw-semibold" style="color: ${getScoreColor(match.preferenceScore)}">${match.preferenceScore}%</span>
                </div>
                <div class="subscore-bar-container">
                    <div class="subscore-bar-fill" style="width: ${match.preferenceScore}%; background: ${getScoreColor(match.preferenceScore)};"></div>
                </div>
            </div>
        `;
    }

    // Skill breakdown
    const matchedSkillsEl = document.getElementById('matchModalMatchedSkills');
    if (matchedSkillsEl) {
        matchedSkillsEl.innerHTML = (match.matchedSkills || []).map(s =>
            `<span class="pill-skill-matched"><i class="bi bi-check-lg" style="font-size: 0.65rem;"></i> ${escapeHtml(s)}</span>`
        ).join('') || '<span class="text-dim small">No matched skills found.</span>';
    }

    const missingSkillsEl = document.getElementById('matchModalMissingSkills');
    if (missingSkillsEl) {
        missingSkillsEl.innerHTML = (match.missingSkills || []).map(s =>
            `<span class="pill-skill-missing"><i class="bi bi-exclamation-sm" style="font-size: 0.65rem;"></i> ${escapeHtml(s)}</span>`
        ).join('') || '<span class="text-success-emphasis small">✓ No critical skill gaps detected.</span>';
    }

    const bonusSkillsEl = document.getElementById('matchModalBonusSkills');
    if (bonusSkillsEl) {
        bonusSkillsEl.innerHTML = (match.bonusSkills || []).map(s =>
            `<span class="pill-skill-bonus"><i class="bi bi-star-fill" style="font-size: 0.6rem;"></i> ${escapeHtml(s)}</span>`
        ).join('') || '<span class="text-dim small">No bonus strengths identified.</span>';
    }

    // Qualitative summary
    const summaryEl = document.getElementById('matchModalSummary');
    if (summaryEl) summaryEl.textContent = match.qualitativeSummary || 'Evaluation complete.';

    // Preparation tips
    const tipsEl = document.getElementById('matchModalTips');
    if (tipsEl) {
        tipsEl.innerHTML = (match.preparationTips || []).map(t =>
            `<li class="mb-2">${escapeHtml(t)}</li>`
        ).join('') || '<li class="text-dim">No specific tips generated.</li>';
    }

    // Apply button
    const matchApplyBtn = document.getElementById('matchModalApplyBtn');
    if (matchApplyBtn) matchApplyBtn.href = match.applyUrl || '#';

    // Metadata
    setText('matchModalEngine', match.matchEngine || 'Deterministic Semantic Matcher v1.0');
    setText('matchModalTimestamp', match.matchedAt ? new Date(match.matchedAt).toLocaleString() : 'Just now');

    const modalEl = document.getElementById('matchDetailModal');
    if (modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }
}

/**
 * Dynamically injects the match detail modal into the DOM if it doesn't exist yet.
 */
function ensureMatchDetailModal() {
    if (document.getElementById('matchDetailModal')) return;

    const modalHtml = `
    <div class="modal fade" id="matchDetailModal" tabindex="-1" aria-labelledby="matchDetailModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-lg modal-dialog-scrollable">
            <div class="modal-content glass-card border-secondary-subtle">
                <div class="modal-header border-secondary-subtle">
                    <div class="w-100">
                        <div class="d-flex justify-content-between align-items-start">
                            <div>
                                <h5 class="modal-title fw-bold" id="matchModalTitle">Job Title</h5>
                                <span class="text-muted small" id="matchModalCompany">Company • Location</span>
                            </div>
                            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="d-flex align-items-center gap-3 mt-2">
                            <span id="matchModalOverallScore" class="fw-black" style="font-size: 2.5rem;">0%</span>
                            <div>
                                <div id="matchModalTier"></div>
                                <div class="text-dim small mt-1">Weighted Qualification Score</div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-body">

                    <!-- Hard Criteria Violations Alert -->
                    <div id="matchModalViolationsBlock" class="alert alert-danger-subtle border border-danger-subtle rounded-3 mb-3 d-none">
                        <div class="fw-semibold text-danger small mb-1"><i class="bi bi-shield-exclamation me-2"></i>Hard Criteria Violations:</div>
                        <ul id="matchModalViolations" class="mb-0 small text-danger-emphasis ps-3"></ul>
                    </div>

                    <div class="row g-4">
                        <!-- Left Column: Sub-score Breakdown -->
                        <div class="col-12 col-md-6">
                            <h6 class="fw-semibold small text-uppercase text-secondary mb-3">Score Breakdown</h6>
                            <div id="matchModalDetailedScores"></div>
                        </div>

                        <!-- Right Column: Skill Analysis -->
                        <div class="col-12 col-md-6">
                            <h6 class="fw-semibold small text-uppercase text-secondary mb-2">Skill Analysis</h6>
                            <div class="mb-3">
                                <div class="text-muted small mb-1">✅ Matched Skills:</div>
                                <div id="matchModalMatchedSkills"></div>
                            </div>
                            <div class="mb-3">
                                <div class="text-muted small mb-1">⚠️ Missing Skills:</div>
                                <div id="matchModalMissingSkills"></div>
                            </div>
                            <div>
                                <div class="text-muted small mb-1">⭐ Bonus Strengths:</div>
                                <div id="matchModalBonusSkills"></div>
                            </div>
                        </div>
                    </div>

                    <!-- Qualitative Summary -->
                    <div class="mt-4">
                        <h6 class="fw-semibold small text-uppercase text-secondary mb-2">AI Qualification Summary</h6>
                        <p id="matchModalSummary" class="text-light-emphasis small p-3 rounded-3" style="background: rgba(0,0,0,0.2); line-height: 1.6;"></p>
                    </div>

                    <!-- Preparation Tips -->
                    <div class="mt-3">
                        <h6 class="fw-semibold small text-uppercase text-secondary mb-2">Interview Preparation Tips</h6>
                        <ul id="matchModalTips" class="text-light-emphasis small ps-4" style="line-height: 1.7;"></ul>
                    </div>

                    <!-- Meta footer -->
                    <div class="d-flex flex-wrap gap-3 mt-3 pt-3 border-top border-secondary-subtle text-dim" style="font-size: 0.78rem;">
                        <span><i class="bi bi-cpu me-1"></i>Engine: <span id="matchModalEngine"></span></span>
                        <span><i class="bi bi-clock me-1"></i>Evaluated: <span id="matchModalTimestamp"></span></span>
                    </div>
                </div>
                <div class="modal-footer border-secondary-subtle d-flex justify-content-between">
                    <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">Close</button>
                    <a href="#" target="_blank" class="btn btn-sm btn-primary d-flex align-items-center gap-1" id="matchModalApplyBtn">
                        <span>Apply on Employer Portal</span>
                        <i class="bi bi-box-arrow-up-right"></i>
                    </a>
                </div>
            </div>
        </div>
    </div>`;

    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

/**
 * Renders match pagination footer
 */
function renderMatchPagination(pageInfo) {
    const paginationEl = document.getElementById('matchPagination');
    const infoEl = document.getElementById('matchPaginationInfo');
    const prevBtn = document.getElementById('matchPrevPageBtn');
    const nextBtn = document.getElementById('matchNextPageBtn');

    if (!paginationEl) return;

    const totalElements = pageInfo.totalElements || 0;
    const totalPages = pageInfo.totalPages || 0;
    const pageNumber = pageInfo.number || 0;
    const pageSize = pageInfo.size || currentMatchSize;

    if (totalElements === 0) {
        paginationEl.classList.add('d-none');
        return;
    }

    paginationEl.classList.remove('d-none');
    const start = pageNumber * pageSize + 1;
    const end = Math.min((pageNumber + 1) * pageSize, totalElements);
    if (infoEl) infoEl.textContent = `Showing ${start}-${end} of ${totalElements} evaluated jobs (Page ${pageNumber + 1} of ${totalPages})`;

    if (prevBtn) prevBtn.disabled = pageNumber <= 0;
    if (nextBtn) nextBtn.disabled = pageNumber >= totalPages - 1;
}

/**
 * Clears all match evaluations via DELETE /api/v1/matches
 */
async function clearAllMatches() {
    try {
        const response = await fetch('/api/v1/matches', { method: 'DELETE' });
        if (response.ok) {
            fetchMatchStats();
            fetchMatchListings(0);
            alert('All AI match evaluations cleared successfully.');
        }
    } catch (err) {
        alert('Failed to clear matches: ' + err.message);
    }
}

/* ==========================================================================
   PHASE 4: MATCH SCORING HELPER UTILITIES
   ========================================================================== */

function getScoreCircleClass(score) {
    if (score >= 75) return 'score-circle score-circle-strong';
    if (score >= 50) return 'score-circle score-circle-moderate';
    return 'score-circle score-circle-weak';
}

function getScoreTextClass(score) {
    if (score >= 75) return 'text-success';
    if (score >= 50) return 'text-info';
    return 'text-danger';
}

function getScoreColor(score) {
    if (score >= 75) return 'var(--accent-emerald)';
    if (score >= 50) return 'var(--accent-cyan)';
    return 'var(--accent-rose)';
}

function getTierBadge(tier, hardCriteriaMet) {
    if (!hardCriteriaMet) {
        return '<span class="badge bg-danger-subtle text-danger border border-danger-subtle px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-x-circle me-1"></i>Criteria Violated</span>';
    }
    switch (tier) {
        case 'STRONG_MATCH':
            return '<span class="badge bg-success-subtle text-success border border-success-subtle px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-trophy me-1"></i>Strong Match</span>';
        case 'MODERATE_MATCH':
            return '<span class="badge bg-info-subtle text-info border border-info-subtle px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-hand-thumbs-up me-1"></i>Moderate Match</span>';
        default:
            return '<span class="badge bg-warning-subtle text-warning border border-warning-subtle px-2 py-1" style="font-size: 0.72rem;"><i class="bi bi-dash-circle me-1"></i>Weak Match</span>';
    }
}

function getTierBorderColor(tier, hardCriteriaMet) {
    if (!hardCriteriaMet) return 'rgba(244, 63, 94, 0.3)';
    switch (tier) {
        case 'STRONG_MATCH': return 'rgba(16, 185, 129, 0.4)';
        case 'MODERATE_MATCH': return 'rgba(6, 182, 212, 0.35)';
        default: return 'rgba(255, 255, 255, 0.08)';
    }
}

/**
 * Builds compact sub-score mini bar HTML for match cards
 */
function buildSubScoreBars(match) {
    const bars = [
        { label: 'Skills', value: match.skillScore, weight: '50%' },
        { label: 'Experience', value: match.experienceScore, weight: '25%' },
        { label: 'Education', value: match.educationScore, weight: '15%' },
        { label: 'Preferences', value: match.preferenceScore, weight: '10%' }
    ];

    return `<div class="mb-2">
        ${bars.map(b => `
            <div class="d-flex align-items-center gap-2 mb-1">
                <span class="text-dim" style="font-size: 0.7rem; width: 72px; flex-shrink: 0;">${b.label}</span>
                <div class="subscore-bar-container flex-grow-1">
                    <div class="subscore-bar-fill" style="width: ${b.value}%; background: ${getScoreColor(b.value)};"></div>
                </div>
                <span style="font-size: 0.7rem; width: 28px; text-align: right; color: ${getScoreColor(b.value)}; font-weight: 600;">${b.value}</span>
            </div>
        `).join('')}
    </div>`;
}

/* ==========================================================================
   PHASE 5: APPLICATION PREPARATION
   ========================================================================== */

function initApplicationPreparation() {
    loadPreparationJobs();
    loadProfileReview();

    const applicationPrepTab = document.getElementById('application-prep-tab');
    if (applicationPrepTab) {
        applicationPrepTab.addEventListener('shown.bs.tab', () => {
            loadPreparationJobs();
            loadProfileReview();
        });
    }

    const prepareBtn = document.getElementById('prepareApplicationBtn');
    if (prepareBtn) {
        prepareBtn.addEventListener('click', prepareApplication);
    }

    const validateBtn = document.getElementById('validateCoverLetterBtn');
    if (validateBtn) {
        validateBtn.addEventListener('click', validateCoverLetter);
    }

    const copyBtn = document.getElementById('copyCoverLetterBtn');
    if (copyBtn) {
        copyBtn.addEventListener('click', copyCoverLetter);
    }

    const submissionConsent = document.getElementById('submissionConsent');
    const coverLetter = document.getElementById('prepCoverLetter');
    const jobSelect = document.getElementById('prepJobSelect');
    const submitBtn = document.getElementById('submitApplicationBtn');
    if (submissionConsent && coverLetter && jobSelect && submitBtn) {
        submissionConsent.addEventListener('change', updateSubmissionButton);
        coverLetter.addEventListener('input', resetSubmissionConfirmation);
        jobSelect.addEventListener('change', () => {
            preparedApplicationJobId = null;
            resetSubmissionConfirmation();
            if (getValue('prepJobSelect')) {
                setText('prepFeedback', 'Prepare a fresh draft for the selected job before confirming an application.');
            }
        });
        submitBtn.addEventListener('click', submitApplication);
    }
}

async function loadPreparationJobs() {
    const select = document.getElementById('prepJobSelect');
    if (!select) return;

    try {
        const response = await fetch('/api/v1/matches?page=0&size=100');
        if (!response.ok) throw new Error(`Could not load evaluated jobs (${response.status}).`);

        const data = await response.json();
        const matches = data.content || [];
        select.replaceChildren(new Option(
            matches.length ? 'Select an evaluated job...' : 'No evaluated jobs. Run Phase 4 matching first.',
            ''
        ));
        matches.forEach(match => {
            const title = match.jobTitle || 'Untitled job';
            const company = match.company || 'Unknown company';
            select.add(new Option(`${title} — ${company} (${match.overallScore}%)`, match.jobId));
        });
        select.disabled = matches.length === 0;
        const prepareBtn = document.getElementById('prepareApplicationBtn');
        if (prepareBtn) prepareBtn.disabled = matches.length === 0;
    } catch (error) {
        select.replaceChildren(new Option('Unable to load evaluated jobs', ''));
        select.disabled = true;
        const prepareBtn = document.getElementById('prepareApplicationBtn');
        if (prepareBtn) prepareBtn.disabled = true;
        setText('prepFeedback', error.message);
    }
}

async function loadProfileReview() {
    try {
        const response = await fetch('/api/v1/application-preparation/profile-review');
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Profile review failed (${response.status}).`);
        renderProfileReview(data);
    } catch (error) {
        setText('profileReviewSummary', error.message);
        setText('profileReviewScore', 'Unavailable');
    }
}

async function prepareApplication() {
    const jobId = getValue('prepJobSelect');
    if (!jobId) {
        setText('prepFeedback', 'Select an evaluated job first.');
        return;
    }

    const button = document.getElementById('prepareApplicationBtn');
    if (button) button.disabled = true;
    preparedApplicationJobId = null;
    resetSubmissionConfirmation();
    setText('prepFeedback', 'Preparing an evidence-grounded draft...');

    try {
        const response = await fetch(`/api/v1/application-preparation/${encodeURIComponent(jobId)}`, {
            method: 'POST',
            headers: { 'Accept': 'application/json' }
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Draft preparation failed (${response.status}).`);

        setText('prepJobHeading', `${data.jobTitle || 'Job'} — ${data.company || 'Company'}`);
        setText('prepGenerationMethod', data.generationMethod || 'Evidence-grounded template');
        setText('prepFeedback', 'Draft prepared. Verify the profile details and edit as needed.');
        setValue('prepCoverLetter', data.coverLetter || '');
        preparedApplicationJobId = jobId;

        const applyLink = document.getElementById('prepApplyLink');
        if (applyLink) {
            const safeApplyUrl = getSafeHttpUrl(data.applyUrl);
            applyLink.removeAttribute('href');
            applyLink.dataset.safeApplyUrl = safeApplyUrl || '';
            applyLink.classList.add('disabled');
            applyLink.setAttribute('aria-disabled', 'true');
            if (data.applyUrl && !safeApplyUrl) {
                setText('prepFeedback', 'Draft prepared, but the job apply URL was omitted because it is not a valid HTTP(S) link.');
            }
        }

        resetSubmissionConfirmation();
        renderEvidence(data.evidence || []);
        renderProfileReview(data.profileReview);
        renderTruthfulness(data.truthfulness);
        const output = document.getElementById('prepOutputSection');
        if (output) output.classList.remove('d-none');
    } catch (error) {
        setText('prepFeedback', error.message);
    } finally {
        if (button) button.disabled = false;
    }
}

function resetSubmissionConfirmation() {
    const consent = document.getElementById('submissionConsent');
    const applyLink = document.getElementById('prepApplyLink');
    const feedback = document.getElementById('submissionFeedback');
    if (consent) consent.checked = false;
    if (applyLink) {
        applyLink.removeAttribute('href');
        applyLink.classList.add('disabled');
        applyLink.setAttribute('aria-disabled', 'true');
    }
    if (feedback) feedback.textContent = '';
    updateSubmissionButton();
}

function updateSubmissionButton() {
    const button = document.getElementById('submitApplicationBtn');
    if (!button) return;
    button.disabled = !getValue('prepJobSelect')
        || preparedApplicationJobId !== getValue('prepJobSelect')
        || !getValue('prepCoverLetter').trim()
        || !document.getElementById('submissionConsent')?.checked;
}

async function submitApplication() {
    const jobId = getValue('prepJobSelect');
    const coverLetter = getValue('prepCoverLetter');
    const consent = document.getElementById('submissionConsent');
    if (!jobId || preparedApplicationJobId !== jobId || !coverLetter.trim() || !consent?.checked) {
        setText('submissionFeedback', 'Prepare the draft for the selected job, review it, and confirm this application first.');
        return;
    }
    const selectedJob = document.getElementById('prepJobSelect').selectedOptions[0]?.textContent || 'this job';
    if (!confirm(`Confirm this application for ${selectedJob}? This confirmation applies only to this job.`)) {
        consent.checked = false;
        updateSubmissionButton();
        return;
    }

    const button = document.getElementById('submitApplicationBtn');
    if (button) button.disabled = true;
    setText('submissionFeedback', 'Recording your confirmation and checking the official provider...');
    try {
        const response = await fetch(`/api/v1/applications/${encodeURIComponent(jobId)}/submit`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ confirmed: true, coverLetter })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Application handoff failed (${response.status}).`);

        const applyLink = document.getElementById('prepApplyLink');
        const safeApplyUrl = getSafeHttpUrl(data.applicationUrl);
        if (data.status === 'LINK_OUT' && safeApplyUrl && applyLink) {
            applyLink.href = safeApplyUrl;
            applyLink.classList.remove('disabled');
            applyLink.setAttribute('aria-disabled', 'false');
            setText('submissionFeedback', `${data.message}${data.alreadyProcessed ? ' This attempt was already recorded.' : ''} Use “Open employer application” to continue; no application was submitted by this app.`);
        } else {
            if (applyLink) {
                applyLink.removeAttribute('href');
                applyLink.classList.add('disabled');
                applyLink.setAttribute('aria-disabled', 'true');
            }
            setText('submissionFeedback', data.message || `Application status: ${data.status}.`);
        }
        consent.checked = false;
    } catch (error) {
        setText('submissionFeedback', error.message);
        consent.checked = false;
    } finally {
        updateSubmissionButton();
    }
}

async function validateCoverLetter() {
    const jobId = getValue('prepJobSelect');
    const coverLetter = getValue('prepCoverLetter');
    if (!jobId || !coverLetter) {
        setText('truthfulnessSummary', 'Choose a job and prepare a non-empty draft before checking claims.');
        return;
    }

    const button = document.getElementById('validateCoverLetterBtn');
    if (button) button.disabled = true;
    setText('truthfulnessSummary', 'Checking likely unsupported claims...');
    try {
        const response = await fetch(`/api/v1/application-preparation/${encodeURIComponent(jobId)}/validate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ coverLetter })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Claim check failed (${response.status}).`);
        renderTruthfulness(data);
    } catch (error) {
        setText('truthfulnessSummary', error.message);
        setText('truthfulnessIssues', '');
    } finally {
        if (button) button.disabled = false;
    }
}

async function copyCoverLetter() {
    try {
        await navigator.clipboard.writeText(getValue('prepCoverLetter'));
        setText('prepFeedback', 'Draft copied to clipboard.');
    } catch (error) {
        setText('prepFeedback', `Could not copy the draft: ${error.message}`);
    }
}

function renderProfileReview(review) {
    if (!review) return;
    const score = document.getElementById('profileReviewScore');
    if (score) {
        score.textContent = `${review.completenessScore}% complete`;
        score.className = review.overallStatus === 'READY'
            ? 'badge bg-success-subtle text-success'
            : review.overallStatus === 'INCOMPLETE'
                ? 'badge bg-danger-subtle text-danger'
                : 'badge bg-warning-subtle text-warning';
    }
    setText('profileReviewSummary',
        `${review.overallStatus.replaceAll('_', ' ')} · ${review.presentFields}/${review.reviewedFields} fields present · Resume ${review.resumeAvailable ? 'available' : 'not uploaded'}`);

    const list = document.getElementById('profileReviewItems');
    if (list) {
        list.innerHTML = (review.items || []).map(item =>
            `<li class="mb-1"><strong>${escapeHtml(item.field)}</strong> — ${escapeHtml(item.status.replaceAll('_', ' '))}: ${escapeHtml(item.message)}</li>`
        ).join('');
    }
}

function renderEvidence(evidence) {
    const list = document.getElementById('prepEvidenceList');
    if (list) {
        list.innerHTML = evidence.length
            ? evidence.map(item => `<li class="mb-1">${escapeHtml(item)}</li>`).join('')
            : '<li>No candidate-specific profile facts were added to this draft.</li>';
    }
}

function renderTruthfulness(result) {
    if (!result) return;
    const summary = document.getElementById('truthfulnessSummary');
    if (summary) {
        summary.textContent = result.allClaimsSupported
            ? `No likely unsupported claims detected. ${result.checkedClaims} claim checks performed.`
            : `Review needed: ${result.unsupportedClaims.length} likely unsupported statement(s) detected.`;
        summary.className = result.allClaimsSupported ? 'small text-success' : 'small text-warning';
    }
    const issues = document.getElementById('truthfulnessIssues');
    if (issues) {
        issues.innerHTML = (result.unsupportedClaims || [])
            .map(claim => `<li class="mb-1">${escapeHtml(claim)}</li>`).join('');
    }
}

function getSafeHttpUrl(value) {
    if (!value) return '';
    try {
        const url = new URL(value, window.location.origin);
        return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : '';
    } catch (error) {
        return '';
    }
}

function initAutomationDashboard() {
    const refreshButton = document.getElementById('refreshAutomationBtn');
    if (!refreshButton) return;

    refreshButton.addEventListener('click', loadAutomationDashboard);
    document.getElementById('startAutomationBtn')?.addEventListener('click', () => updateAutomationState('start'));
    document.getElementById('pauseAutomationBtn')?.addEventListener('click', () => updateAutomationState('pause'));
    document.getElementById('stopAutomationBtn')?.addEventListener('click', () => updateAutomationState('stop'));
    document.getElementById('automation-tab')?.addEventListener('shown.bs.tab', loadAutomationDashboard);
    loadAutomationDashboard();
}

async function loadAutomationDashboard() {
    const statusMessage = document.getElementById('automationStatusMessage');
    const runsContainer = document.getElementById('automationRuns');
    try {
        const [statusResponse, runsResponse] = await Promise.all([
            fetch('/api/v1/automation'),
            fetch('/api/v1/automation/runs')
        ]);
        if (!statusResponse.ok || !runsResponse.ok) {
            throw new Error('Unable to load background automation status and run history.');
        }

        const status = await statusResponse.json();
        const runs = await runsResponse.json();
        renderAutomationStatus(status);
        renderAutomationRuns(runs);
    } catch (error) {
        if (statusMessage) statusMessage.textContent = error.message;
        if (runsContainer) runsContainer.textContent = 'Run history is unavailable.';
    }
}

function renderAutomationStatus(status) {
    const badge = document.getElementById('automationStatusBadge');
    const state = status.status || 'STOPPED';
    const badgeStyle = state === 'RUNNING'
        ? 'bg-success-subtle text-success'
        : state === 'PAUSED'
            ? 'bg-warning-subtle text-warning'
            : 'bg-secondary-subtle text-secondary';
    if (badge) {
        badge.textContent = state;
        badge.className = `badge ${badgeStyle}`;
    }

    setText('automationInterval', `${status.intervalMinutes || 360} minutes`);
    setText('automationStatusMessage', status.lastMessage || 'Automation is stopped. Start it when you are ready.');
    setText('automationLastRun', formatAutomationTimestamp(status.lastRunAt, 'Not yet run'));
    setText('automationNextRun', formatAutomationTimestamp(status.nextRunAt, 'Not scheduled'));
    setText('automationEmailStatus', status.emailConfigured ? 'Configured' : 'Not configured; in-app alerts remain available');

    const running = state === 'RUNNING';
    const startButton = document.getElementById('startAutomationBtn');
    const pauseButton = document.getElementById('pauseAutomationBtn');
    const stopButton = document.getElementById('stopAutomationBtn');
    if (startButton) startButton.disabled = running;
    if (pauseButton) pauseButton.disabled = !running;
    if (stopButton) stopButton.disabled = state === 'STOPPED';
}

function renderAutomationRuns(runs) {
    const container = document.getElementById('automationRuns');
    if (!container) return;
    if (!Array.isArray(runs) || runs.length === 0) {
        container.textContent = 'No scheduled runs yet. Start automation to begin six-hour discovery and matching.';
        return;
    }

    container.innerHTML = runs.map(run => {
        const state = run.success ? 'Completed' : 'Needs attention';
        const badgeClass = run.success ? 'text-success' : 'text-warning';
        const summary = run.newJobs > 0
            ? `${run.newJobs} new job(s), ${run.strongMatches} strong match(es)`
            : 'No new jobs found';
        const email = run.emailStatus && run.emailStatus !== 'NO_NEW_JOBS'
            ? ` • Email: ${run.emailStatus.replaceAll('_', ' ').toLowerCase()}`
            : '';
        return `<div class="border-bottom border-secondary-subtle py-3">
            <div class="d-flex justify-content-between gap-2">
                <strong>${escapeHtml(formatAutomationTimestamp(run.completedAt || run.startedAt, 'Run time unavailable'))}</strong>
                <span class="${badgeClass}">${state}</span>
            </div>
            <div class="text-light-emphasis">${escapeHtml(summary)}${escapeHtml(email)}</div>
            <div class="text-muted mt-1">${escapeHtml(run.message || '')}</div>
        </div>`;
    }).join('');
}

function formatAutomationTimestamp(value, fallback) {
    if (!value) return fallback;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? fallback : date.toLocaleString();
}

async function updateAutomationState(action) {
    const statusMessage = document.getElementById('automationStatusMessage');
    const buttons = ['startAutomationBtn', 'pauseAutomationBtn', 'stopAutomationBtn']
        .map(id => document.getElementById(id))
        .filter(Boolean);
    buttons.forEach(button => { button.disabled = true; });
    try {
        const response = await fetch(`/api/v1/automation/${action}`, { method: 'POST' });
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || `Unable to ${action} automation.`);
        renderAutomationStatus(data);
        await loadAutomationDashboard();
    } catch (error) {
        if (statusMessage) statusMessage.textContent = error.message;
        buttons.forEach(button => { button.disabled = false; });
    }
}
