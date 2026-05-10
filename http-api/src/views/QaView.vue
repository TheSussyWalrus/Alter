<template>
  <div class="qa-page">
    <header class="hero">
      <div>
        <p class="eyebrow">World QA</p>
        <h1>AI QA Characters</h1>
        <p class="lede">
          Run deterministic QA characters against skills, world routes, and gameplay loops, then inspect session reports from one central place.
        </p>
      </div>
      <div class="status-card" :class="{ running: qaStatus && qaStatus.running }">
        <span>{{ qaStatusLabel }}</span>
        <strong>{{ qaActiveSessions.length }}</strong>
        <small>active sessions</small>
      </div>
    </header>

    <section v-if="error" class="notice error">{{ error }}</section>
    <section v-if="status" class="notice">{{ status }}</section>

    <section class="qa-banner">
      <div>
        <strong>QA Control Center</strong>
        <small>{{ qaSuites.length }} suites / {{ qaScenarios.length }} scenarios / {{ qaSessions.length }} recent sessions</small>
      </div>
      <div class="banner-actions">
        <button type="button" @click="fetchQaPanel" :disabled="qaLoading">Refresh QA</button>
        <button type="button" class="ghost" @click="stopQaSession(qaSelectedSessionId)" :disabled="qaLoading || !qaSelectedSessionId">
          Stop selected
        </button>
      </div>
    </section>

    <main class="qa-shell">
      <section class="qa-grid">
        <article class="qa-card">
          <div class="qa-card-title">
            <h3>Status</h3>
            <span v-if="qaLoading" class="inline-status">Loading...</span>
          </div>
          <div class="qa-status-grid">
            <span>
              <strong>{{ qaStatusLabel }}</strong>
              <small>Runner</small>
            </span>
            <span>
              <strong>{{ qaActiveSessions.length }}</strong>
              <small>Active</small>
            </span>
            <span>
              <strong>{{ qaScenarios.length }}</strong>
              <small>Scenarios</small>
            </span>
            <span>
              <strong>{{ qaSuites.length }}</strong>
              <small>Suites</small>
            </span>
          </div>
          <p v-if="qaStatusMessage" class="qa-muted">{{ qaStatusMessage }}</p>
          <p v-if="qaSessionsPath" class="qa-path">{{ qaSessionsPath }}</p>
          <p v-if="qaFixtureSummary" class="qa-muted">{{ qaFixtureSummary }}</p>
        </article>

        <article class="qa-card">
          <div class="qa-card-title">
            <h3>Start Playtest</h3>
          </div>
          <label>
            Playtest suite
            <select v-model="qaSelectedSuiteId">
              <option value="">Use legacy scenario instead</option>
              <option v-for="suite in qaSuites" :key="qaSuiteId(suite)" :value="qaSuiteId(suite)">
                {{ qaSuiteLabel(suite) }}
              </option>
            </select>
          </label>
          <div v-if="qaSelectedSuite" class="qa-scenario-note">
            {{ qaSelectedSuite.description || 'No suite description provided.' }}
          </div>
          <label>
            Legacy scenario
            <select v-model="qaSelectedScenarioId" :disabled="!!qaSelectedSuiteId">
              <option value="skills-basic">skills-basic</option>
              <option v-for="scenario in qaVisibleScenarios" :key="qaScenarioId(scenario)" :value="qaScenarioId(scenario)">
                {{ qaScenarioLabel(scenario) }}
              </option>
            </select>
          </label>
          <div v-if="qaSelectedScenario" class="qa-scenario-note">
            {{ qaSelectedScenario.description || qaSelectedScenario.summary || 'No description provided.' }}
          </div>
          <div class="qa-options-row">
            <label>
              Repeat count
              <input v-model.number="qaRepeatCount" type="number" min="1" max="25">
            </label>
            <label class="qa-check">
              <input v-model="qaContinueOnFailure" type="checkbox">
              Continue after failures
            </label>
          </div>
          <div class="actions">
            <button type="button" @click="startQaSession" :disabled="qaLoading">Start QA playtest</button>
          </div>
        </article>
      </section>

      <section class="qa-layout">
        <article class="qa-card">
          <div class="qa-card-title">
            <h3>Recent Sessions</h3>
            <button type="button" class="ghost" @click="fetchQaSessions" :disabled="qaLoading">Refresh</button>
          </div>
          <div class="qa-session-list">
            <button
              v-for="session in qaSessions"
              :key="qaSessionId(session)"
              type="button"
              class="qa-session-row"
              :class="{ selected: qaSelectedSessionId === qaSessionId(session) }"
              @click="selectQaSession(qaSessionId(session))"
            >
              <span>
                <strong>{{ session.scenarioId || session.scenario || 'QA session' }}</strong>
                <small>{{ qaSessionId(session) }}</small>
              </span>
              <em>{{ session.status || session.state || 'unknown' }}</em>
            </button>
            <p v-if="!qaSessions.length" class="qa-empty">No QA sessions yet. Start a scenario to create one.</p>
          </div>
        </article>

        <article class="qa-card qa-detail">
          <div class="qa-card-title">
            <h3>Session Detail</h3>
            <span v-if="qaDetailLoading" class="inline-status">Loading...</span>
          </div>
          <div v-if="qaSelectedSession" class="qa-detail-body">
            <div class="qa-meta">
              <span><strong>{{ qaSelectedSession.status || qaSelectedSession.state || 'unknown' }}</strong><small>Status</small></span>
              <span><strong>{{ qaSelectedSession.scenarioId || qaSelectedSession.scenario || 'unknown' }}</strong><small>Scenario</small></span>
              <span><strong>{{ qaSelectedSession.startedAt || qaSelectedSession.createdAt || '-' }}</strong><small>Started</small></span>
              <span><strong>{{ qaSelectedSession.finishedAt || '-' }}</strong><small>Finished</small></span>
            </div>
            <div class="actions">
              <button type="button" class="ghost" @click="downloadQaReport" :disabled="!qaSelectedSession">Download JSON report</button>
            </div>

            <section v-if="qaSelectedSessionJourneys.length" class="qa-section qa-journeys-section">
              <div class="qa-section-heading">
                <h4>Journeys</h4>
                <div class="qa-step-summary">
                  <span class="passed">{{ qaPassedJourneyCount }} passed</span>
                  <span class="failed">{{ qaFailedJourneyCount }} failed</span>
                </div>
              </div>
              <div class="qa-journey-cards">
                <article
                  v-for="journey in qaSelectedSessionJourneys"
                  :key="journey.id"
                  class="qa-journey-card"
                  :class="qaStatusClass(journey.status)"
                >
                  <header class="qa-step-header">
                    <div>
                      <strong>{{ journey.name || journey.id }}</strong>
                      <small>{{ journey.category || 'journey' }} / {{ journey.goals ? journey.goals.length : 0 }} goals</small>
                    </div>
                    <span class="qa-badge" :class="qaStatusClass(journey.status)">{{ journey.status || 'unknown' }}</span>
                  </header>
                  <p v-if="journey.failureClass" class="qa-muted">Failure: {{ journey.failureClass }}</p>
                  <div v-if="journey.goals && journey.goals.length" class="qa-mini-goals">
                    <span
                      v-for="goal in journey.goals"
                      :key="`${journey.id}-${goal.id}-${goal.attempt}`"
                      :class="qaStatusClass(goal.status)"
                    >
                      {{ goal.id }} · attempt {{ goal.attempt }}/{{ goal.maxAttempts }}
                    </span>
                  </div>
                </article>
              </div>
            </section>

            <section class="qa-section qa-steps-section">
              <div class="qa-section-heading">
                <h4>Steps</h4>
                <div class="qa-step-summary">
                  <span class="passed">{{ qaPassedStepCount }} passed</span>
                  <span class="failed">{{ qaFailedStepCount }} failed</span>
                  <span>{{ qaRunningStepCount }} running</span>
                </div>
              </div>
              <div v-if="qaSelectedSessionSteps.length" class="qa-step-cards">
                <article
                  v-for="(step, index) in qaSelectedSessionSteps"
                  :key="`step-${index}`"
                  class="qa-step-card"
                  :class="qaStatusClass(step.status)"
                >
                  <header class="qa-step-header">
                    <div>
                      <strong>{{ qaStepTitle(step, index) }}</strong>
                      <small>{{ step.skill || 'no skill' }} / {{ step.category || step.type || 'probe' }}</small>
                    </div>
                    <span class="qa-badge" :class="qaStatusClass(step.status)">{{ step.status || 'unknown' }}</span>
                  </header>

                  <div class="qa-step-meta">
                    <span v-if="step.failureClass" class="failure">Failure: {{ step.failureClass }}</span>
                    <span v-if="qaStepDuration(step)">Duration: {{ qaStepDuration(step) }}</span>
                    <span v-if="step.type">Type: {{ step.type }}</span>
                  </div>

                  <div v-if="step.assertions && step.assertions.length" class="qa-assertion-table">
                    <div
                      v-for="(assertion, assertionIndex) in step.assertions"
                      :key="`step-${index}-assertion-${assertionIndex}`"
                      class="qa-assertion-row"
                      :class="{ failed: qaAssertionFailed(assertion), passed: !qaAssertionFailed(assertion) }"
                    >
                      <span class="qa-assertion-state">{{ qaAssertionFailed(assertion) ? 'Fail' : 'Pass' }}</span>
                      <span class="qa-assertion-name">{{ assertion.name || assertion.id || 'assertion' }}</span>
                      <span class="qa-assertion-detail">{{ qaAssertionDetail(assertion) }}</span>
                    </div>
                  </div>
                  <p v-else class="qa-empty">No assertions recorded for this step.</p>

                  <details v-if="step.observations && step.observations.length" class="qa-details">
                    <summary>Observations</summary>
                    <ul>
                      <li v-for="(item, observationIndex) in step.observations" :key="`step-${index}-observation-${observationIndex}`">
                        {{ formatQaItem(item) }}
                      </li>
                    </ul>
                  </details>

                  <details v-if="step.messages && step.messages.length" class="qa-details">
                    <summary>Captured messages</summary>
                    <ul>
                      <li v-for="(message, messageIndex) in step.messages" :key="`step-${index}-message-${messageIndex}`">
                        {{ message }}
                      </li>
                    </ul>
                  </details>
                </article>
              </div>
              <p v-else class="qa-empty">No steps recorded.</p>
            </section>

            <section class="qa-section">
              <h4>Assertions</h4>
              <ul v-if="qaSelectedSessionAssertions.length" class="qa-list">
                <li v-for="(item, index) in qaSelectedSessionAssertions" :key="`assertion-${index}`" :class="{ failed: qaAssertionFailed(item) }">
                  {{ formatQaItem(item) }}
                </li>
              </ul>
              <p v-else class="qa-empty">No assertions recorded.</p>
            </section>

            <section class="qa-section">
              <h4>Warnings</h4>
              <ul v-if="qaSelectedSessionWarnings.length" class="qa-list warning">
                <li v-for="(item, index) in qaSelectedSessionWarnings" :key="`warning-${index}`">{{ formatQaItem(item) }}</li>
              </ul>
              <p v-else class="qa-empty">No warnings recorded.</p>
            </section>

            <section class="qa-section">
              <h4>Event Timeline</h4>
              <ul v-if="qaSelectedSessionEvents.length" class="qa-list qa-events">
                <li v-for="(event, index) in qaSelectedSessionEvents" :key="`event-${index}`">
                  <strong>{{ event.type || 'event' }}</strong>
                  <span>{{ event.message || formatQaItem(event) }}</span>
                </li>
              </ul>
              <p v-else class="qa-empty">No events recorded.</p>
            </section>
          </div>
          <p v-else class="qa-empty">Select a session to inspect its run details.</p>
        </article>
      </section>
    </main>
  </div>
</template>

<script>
import axios from 'axios';

const API = 'http://127.0.0.1:4567';

export default {
  name: 'QaView',
  data() {
    return {
      error: '',
      status: '',
      qaLoading: false,
      qaDetailLoading: false,
      qaStatus: null,
      qaScenarios: [],
      qaSuites: [],
      qaSessions: [],
      qaSelectedScenarioId: 'skills-basic',
      qaSelectedSuiteId: 'core-playtest',
      qaSelectedSessionId: '',
      qaSelectedSession: null,
      qaFixtureStatus: null,
      qaRepeatCount: 1,
      qaContinueOnFailure: true
    };
  },
  computed: {
    qaActiveSessions() {
      return this.qaSessions.filter(session => ['running', 'active', 'starting'].includes(String(session.status || session.state || '').toLowerCase()));
    },
    qaSelectedScenario() {
      return this.qaScenarios.find(scenario => this.qaScenarioId(scenario) === this.qaSelectedScenarioId);
    },
    qaSelectedSuite() {
      return this.qaSuites.find(suite => this.qaSuiteId(suite) === this.qaSelectedSuiteId);
    },
    qaVisibleScenarios() {
      return this.qaScenarios.filter(scenario => this.qaScenarioId(scenario) !== 'skills-basic');
    },
    qaStatusLabel() {
      if (!this.qaStatus) {
        return 'Unknown';
      }
      if (this.qaStatus.running) {
        return 'Running';
      }
      return this.qaStatus.status || this.qaStatus.state || (this.qaStatus.enabled === false ? 'Disabled' : 'Ready');
    },
    qaStatusMessage() {
      if (!this.qaStatus) {
        return 'QA status has not been loaded yet.';
      }
      return this.qaStatus.message || this.qaStatus.detail || this.qaStatus.error || '';
    },
    qaSessionsPath() {
      return this.qaStatus?.sessionsPath || '';
    },
    qaFixtureSummary() {
      const fixtures = this.qaFixtureStatus || this.qaStatus?.fixtures;
      if (!fixtures) {
        return '';
      }
      return `Fixtures: ${fixtures.tempNpcs || 0} temp NPCs / ${fixtures.tempObjects || 0} temp objects active.`;
    },
    qaSelectedSessionJourneys() {
      return this.qaSelectedSession?.journeys || [];
    },
    qaSelectedSessionEvents() {
      return this.qaSelectedSession?.events || [];
    },
    qaSelectedSessionSteps() {
      return this.qaSelectedSession?.steps || [];
    },
    qaSelectedSessionAssertions() {
      return this.qaSelectedSession?.assertions || [];
    },
    qaSelectedSessionWarnings() {
      return this.qaSelectedSession?.warnings || [];
    },
    qaPassedStepCount() {
      return this.qaSelectedSessionSteps.filter(step => String(step.status || '').toLowerCase() === 'passed').length;
    },
    qaFailedStepCount() {
      return this.qaSelectedSessionSteps.filter(step => String(step.status || '').toLowerCase() === 'failed').length;
    },
    qaRunningStepCount() {
      return this.qaSelectedSessionSteps.filter(step => ['running', 'active', 'starting'].includes(String(step.status || '').toLowerCase())).length;
    },
    qaPassedJourneyCount() {
      return this.qaSelectedSessionJourneys.filter(journey => String(journey.status || '').toLowerCase() === 'passed').length;
    },
    qaFailedJourneyCount() {
      return this.qaSelectedSessionJourneys.filter(journey => String(journey.status || '').toLowerCase() === 'failed').length;
    }
  },
  created() {
    this.fetchQaPanel();
  },
  methods: {
    async fetchQaPanel() {
      this.qaLoading = true;
      this.error = '';
      try {
        const [status, scenarios, suites, sessions, fixtures] = await Promise.all([
          axios.get(`${API}/world-editor/qa/status`),
          axios.get(`${API}/world-editor/qa/scenarios`),
          axios.get(`${API}/world-editor/qa/suites`),
          axios.get(`${API}/world-editor/qa/sessions`),
          axios.get(`${API}/world-editor/qa/fixtures/status`)
        ]);
        this.qaStatus = status.data || null;
        this.qaScenarios = this.qaArrayFromResponse(scenarios.data, 'scenarios');
        this.qaSuites = this.qaArrayFromResponse(suites.data, 'suites');
        this.qaSessions = this.qaArrayFromResponse(sessions.data, 'sessions');
        this.qaFixtureStatus = fixtures.data || null;
        this.ensureQaSelection();
        if (this.qaSelectedSessionId) {
          await this.fetchQaSession(this.qaSelectedSessionId);
        }
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.qaLoading = false;
      }
    },
    async fetchQaSessions() {
      this.qaLoading = true;
      this.error = '';
      try {
        const res = await axios.get(`${API}/world-editor/qa/sessions`);
        this.qaSessions = this.qaArrayFromResponse(res.data, 'sessions');
        this.ensureQaSelection();
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.qaLoading = false;
      }
    },
    async startQaSession() {
      this.qaLoading = true;
      this.error = '';
      try {
        const payload = {
          repeatCount: Math.max(1, Math.min(25, Number(this.qaRepeatCount) || 1)),
          continueOnFailure: !!this.qaContinueOnFailure,
          fixtureMode: 'ephemeral'
        };
        if (this.qaSelectedSuiteId) {
          payload.suiteId = this.qaSelectedSuiteId;
        } else {
          payload.scenarioId = this.qaSelectedScenarioId || 'skills-basic';
        }
        const res = await axios.post(`${API}/world-editor/qa/sessions`, payload);
        const session = res.data?.session || res.data;
        this.status = `Started QA ${this.qaSelectedSuiteId ? 'suite' : 'scenario'} ${this.qaSelectedSuiteId || this.qaSelectedScenarioId}.`;
        if (session && this.qaSessionId(session)) {
          this.qaSelectedSessionId = this.qaSessionId(session);
          this.qaSelectedSession = session;
        }
        await this.fetchQaPanel();
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.qaLoading = false;
      }
    },
    async stopQaSession(sessionId) {
      if (!sessionId) {
        return;
      }
      this.qaLoading = true;
      this.error = '';
      try {
        const res = await axios.post(`${API}/world-editor/qa/sessions/${encodeURIComponent(sessionId)}/stop`);
        this.status = `Stopped QA session ${sessionId}.`;
        this.qaSelectedSession = res.data?.session || res.data || this.qaSelectedSession;
        await this.fetchQaPanel();
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.qaLoading = false;
      }
    },
    async selectQaSession(sessionId) {
      this.qaSelectedSessionId = sessionId;
      await this.fetchQaSession(sessionId);
    },
    async fetchQaSession(sessionId) {
      if (!sessionId) {
        this.qaSelectedSession = null;
        return;
      }
      this.qaDetailLoading = true;
      this.error = '';
      try {
        const res = await axios.get(`${API}/world-editor/qa/sessions/${encodeURIComponent(sessionId)}`);
        this.qaSelectedSession = res.data?.session || res.data;
      } catch (err) {
        this.error = this.errorMessage(err);
      } finally {
        this.qaDetailLoading = false;
      }
    },
    ensureQaSelection() {
      if (this.qaSelectedSuiteId && !this.qaSuites.some(suite => this.qaSuiteId(suite) === this.qaSelectedSuiteId)) {
        this.qaSelectedSuiteId = this.qaSuites[0] ? this.qaSuiteId(this.qaSuites[0]) : '';
      }
      if (!this.qaSelectedScenarioId) {
        this.qaSelectedScenarioId = 'skills-basic';
      }
      const selectedStillExists = this.qaSessions.some(session => this.qaSessionId(session) === this.qaSelectedSessionId);
      if (!selectedStillExists) {
        const first = this.qaSessions[0];
        this.qaSelectedSessionId = first ? this.qaSessionId(first) : '';
        this.qaSelectedSession = first || null;
      }
      if (this.qaSelectedSessionId && (!this.qaSelectedSession || this.qaSessionId(this.qaSelectedSession) !== this.qaSelectedSessionId)) {
        const summary = this.qaSessions.find(session => this.qaSessionId(session) === this.qaSelectedSessionId);
        this.qaSelectedSession = summary || null;
      }
    },
    qaArrayFromResponse(data, key) {
      if (Array.isArray(data)) {
        return data;
      }
      if (Array.isArray(data?.[key])) {
        return data[key];
      }
      if (Array.isArray(data?.results)) {
        return data.results;
      }
      if (Array.isArray(data?.items)) {
        return data.items;
      }
      return [];
    },
    qaScenarioId(scenario) {
      return String(scenario?.id || scenario?.scenarioId || scenario?.key || scenario?.name || '');
    },
    qaScenarioLabel(scenario) {
      const id = this.qaScenarioId(scenario);
      const name = scenario?.name || scenario?.title || id;
      return id && name !== id ? `${name} (${id})` : name;
    },
    qaSuiteId(suite) {
      return String(suite?.id || suite?.suiteId || suite?.key || suite?.name || '');
    },
    qaSuiteLabel(suite) {
      const id = this.qaSuiteId(suite);
      const name = suite?.name || suite?.title || id;
      return id && name !== id ? `${name} (${id})` : name;
    },
    qaSessionId(session) {
      return String(session?.id || session?.sessionId || session?.key || '');
    },
    formatQaItem(item) {
      if (item == null) {
        return '';
      }
      if (typeof item === 'string') {
        return item;
      }
      return item.message || item.description || item.summary || item.name || JSON.stringify(item);
    },
    qaStepTitle(step, index) {
      const id = step?.id || step?.stepId || `Step ${index + 1}`;
      return `${index + 1}. ${id}`;
    },
    qaStepDuration(step) {
      const start = Number(step?.startedAtCycle);
      const finish = Number(step?.finishedAtCycle);
      if (!Number.isFinite(start) || !Number.isFinite(finish) || finish < start) {
        return '';
      }
      const ticks = finish - start;
      return `${ticks} tick${ticks === 1 ? '' : 's'}`;
    },
    qaStatusClass(status) {
      const value = String(status || 'unknown').toLowerCase();
      if (['passed', 'pass', 'success'].includes(value)) {
        return 'passed';
      }
      if (['failed', 'fail', 'error'].includes(value)) {
        return 'failed';
      }
      if (['running', 'active', 'starting'].includes(value)) {
        return 'running';
      }
      if (['stopped', 'skipped'].includes(value)) {
        return 'stopped';
      }
      return 'unknown';
    },
    qaAssertionFailed(item) {
      if (!item || typeof item === 'string') {
        return false;
      }
      return item.passed === false || item.success === false || String(item.status || '').toLowerCase() === 'failed';
    },
    qaAssertionDetail(assertion) {
      const expected = assertion && assertion.expected != null ? assertion.expected : '-';
      const actual = assertion && assertion.actual != null ? assertion.actual : '-';
      return `Expected ${expected} / Actual ${actual}`;
    },
    downloadQaReport() {
      if (!this.qaSelectedSession) {
        return;
      }
      const id = this.qaSessionId(this.qaSelectedSession) || 'qa-report';
      const blob = new Blob([JSON.stringify(this.qaSelectedSession, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${id}.json`;
      link.click();
      URL.revokeObjectURL(url);
    },
    errorMessage(err) {
      return err?.response?.data?.error || err?.response?.data?.message || err?.message || String(err);
    }
  }
};
</script>

<style scoped>
.qa-page {
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at 10% 10%, rgba(220, 202, 171, 0.44), transparent 28%),
    linear-gradient(135deg, #fff9eb 0%, #f2f5ed 42%, #e6ece2 100%);
  color: #26372d;
  font-family: "Poppins", sans-serif;
}

.hero,
.qa-banner,
.qa-card-title,
.qa-meta,
.qa-status-grid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.hero {
  margin-bottom: 20px;
}

.eyebrow,
label,
.qa-section h4 {
  margin: 0;
  color: #4f5d50;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1,
h3 {
  margin: 4px 0 0;
  color: #172820;
}

.lede {
  max-width: 720px;
  margin-top: 8px;
  color: #657365;
  line-height: 1.45;
}

.status-card {
  min-width: 180px;
  padding: 18px;
  border-radius: 20px;
  background: #f6eddb;
  box-shadow: 0 16px 40px rgba(38, 55, 45, 0.12);
}

.status-card.running {
  background: #26372d;
  color: #fff9eb;
}

.status-card span,
.status-card small {
  display: block;
  opacity: 0.78;
}

.status-card strong {
  display: block;
  margin: 4px 0;
  font-size: 40px;
}

.notice {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #dfe8d8;
  color: #26372d;
}

.notice.error {
  background: #ffe1d7;
  color: #9f3117;
}

.qa-banner,
.qa-card {
  border: 1px solid rgba(38, 55, 45, 0.12);
  border-radius: 22px;
  background: rgba(255, 253, 247, 0.84);
  box-shadow: 0 14px 32px rgba(38, 55, 45, 0.08);
}

.qa-banner {
  margin-bottom: 16px;
  padding: 18px;
}

.qa-banner small,
.qa-muted,
.qa-empty,
.qa-scenario-note,
.qa-path,
.qa-status-grid small,
.qa-meta small {
  color: #6e786f;
}

.banner-actions,
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

button {
  border: 0;
  border-radius: 14px;
  padding: 12px 16px;
  background: #26372d;
  color: #fff9eb;
  font-weight: 800;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

button.ghost {
  background: #d9c8aa;
  color: #26372d;
}

.qa-shell,
.qa-session-list,
.qa-detail-body,
.qa-section {
  display: grid;
  gap: 16px;
}

.qa-grid,
.qa-layout {
  display: grid;
  gap: 16px;
}

.qa-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.qa-layout {
  grid-template-columns: minmax(280px, 380px) 1fr;
  align-items: start;
}

.qa-card {
  display: grid;
  gap: 14px;
  padding: 18px;
}

label {
  display: grid;
  gap: 8px;
}

select,
input[type="number"] {
  width: 100%;
  border: 1px solid rgba(38, 55, 45, 0.22);
  border-radius: 14px;
  padding: 12px 14px;
  background: #fffdf7;
  color: #26372d;
  font: inherit;
}

.qa-options-row {
  display: grid;
  grid-template-columns: minmax(120px, 180px) 1fr;
  gap: 12px;
  align-items: end;
}

.qa-check {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  letter-spacing: 0;
  text-transform: none;
}

.qa-check input {
  width: auto;
}

.inline-status {
  color: #6e786f;
  font-size: 13px;
}

.qa-status-grid span,
.qa-meta span {
  display: grid;
  flex: 1;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: #f6eddb;
}

.qa-status-grid strong,
.qa-meta strong {
  color: #26372d;
}

.qa-path {
  overflow-wrap: anywhere;
  font-size: 12px;
}

.qa-session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid transparent;
  background: #f6eddb;
  color: #26372d;
  text-align: left;
}

.qa-session-row.selected {
  border-color: #26372d;
  background: #26372d;
  color: #fff9eb;
}

.qa-session-row small,
.qa-session-row em {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-style: normal;
  opacity: 0.75;
}

.qa-section {
  padding: 14px;
  border-radius: 16px;
  background: #f6eddb;
}

.qa-section-heading,
.qa-step-header,
.qa-step-meta,
.qa-assertion-row,
.qa-step-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qa-section-heading,
.qa-step-header,
.qa-assertion-row {
  justify-content: space-between;
}

.qa-step-summary {
  flex-wrap: wrap;
  justify-content: flex-end;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.qa-step-summary span,
.qa-badge,
.qa-step-meta span {
  border-radius: 999px;
  padding: 6px 10px;
  background: #fff9eb;
  color: #4f5d50;
}

.qa-step-summary .passed,
.qa-badge.passed {
  background: #dfe8d8;
  color: #1f5b35;
}

.qa-step-summary .failed,
.qa-badge.failed {
  background: #ffe1d7;
  color: #9f3117;
}

.qa-badge.running {
  background: #26372d;
  color: #fff9eb;
}

.qa-badge.stopped,
.qa-badge.unknown {
  background: #d9c8aa;
  color: #26372d;
}

.qa-step-cards,
.qa-journey-cards {
  display: grid;
  gap: 12px;
}

.qa-step-card,
.qa-journey-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(38, 55, 45, 0.12);
  border-radius: 16px;
  background: #fffdf7;
}

.qa-step-card.failed,
.qa-journey-card.failed {
  border-color: rgba(159, 49, 23, 0.38);
}

.qa-step-card.passed,
.qa-journey-card.passed {
  border-color: rgba(31, 91, 53, 0.24);
}

.qa-step-header small {
  display: block;
  margin-top: 4px;
  color: #6e786f;
}

.qa-step-meta {
  flex-wrap: wrap;
  font-size: 12px;
}

.qa-step-meta .failure {
  background: #ffe1d7;
  color: #9f3117;
  font-weight: 800;
}

.qa-assertion-table {
  display: grid;
  gap: 8px;
}

.qa-assertion-row {
  align-items: flex-start;
  display: grid;
  grid-template-columns: 56px minmax(150px, 0.7fr) minmax(220px, 1fr);
  padding: 10px;
  border-radius: 12px;
  background: #f6eddb;
  line-height: 1.35;
}

.qa-assertion-row.failed {
  background: #ffe1d7;
  color: #7b2814;
}

.qa-assertion-row.passed {
  background: #e8f1df;
  color: #26372d;
}

.qa-assertion-state,
.qa-assertion-name {
  font-weight: 800;
}

.qa-assertion-detail {
  overflow-wrap: anywhere;
}

.qa-details {
  border-radius: 12px;
  background: #f9f2e4;
  color: #435044;
}

.qa-details summary {
  cursor: pointer;
  padding: 10px 12px;
  font-weight: 800;
}

.qa-details ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0 14px 12px 30px;
  line-height: 1.45;
}

.qa-mini-goals {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.qa-mini-goals span {
  border-radius: 999px;
  padding: 6px 10px;
  background: #f6eddb;
  color: #4f5d50;
  font-size: 12px;
  font-weight: 800;
}

.qa-mini-goals span.passed {
  background: #dfe8d8;
  color: #1f5b35;
}

.qa-mini-goals span.failed {
  background: #ffe1d7;
  color: #9f3117;
}

.qa-list,
.qa-ordered {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 22px;
  color: #435044;
  line-height: 1.45;
}

.qa-list.warning li,
.qa-list li.failed {
  color: #9f3117;
  font-weight: 800;
}

.qa-events li {
  display: grid;
  gap: 2px;
}

.qa-events span {
  color: #435044;
}

@media (max-width: 960px) {
  .hero,
  .qa-banner {
    align-items: stretch;
    flex-direction: column;
  }

  .qa-grid,
  .qa-layout,
  .qa-options-row {
    grid-template-columns: 1fr;
  }

  .qa-assertion-row {
    grid-template-columns: 1fr;
  }
}
</style>
