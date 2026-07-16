<template>
  <div class="tutorial-view">
    <header class="page-header">
      <h2>Tutorial</h2>
      <p>Follow the core VibeGraph workflow from import to impact analysis.</p>
    </header>

    <ol class="tutorial-steps">
      <li>
        <span>1</span>
        <div>
          <h3>Import a repository</h3>
          <p>
            Open Repositories and import a Java project from a local folder, archive, or GitHub URL.
          </p>
        </div>
      </li>
      <li>
        <span>2</span>
        <div>
          <h3>Open the graph</h3>
          <p>
            Choose an analyzed repository to inspect packages, classes, methods, and relationships.
          </p>
        </div>
      </li>
      <li>
        <span>3</span>
        <div>
          <h3>Run impact analysis</h3>
          <p>Select a symbol and analyze upstream dependents before changing code.</p>
        </div>
      </li>
      <li>
        <span>4</span>
        <div>
          <h3>Use reports when stuck</h3>
          <p>Send a report thread with project or quota issues so support can follow up.</p>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.tutorial-view {
  display: flex;
  flex-direction: column;
  gap: var(--vg-space-6);
}

.page-header h2 {
  margin: 0 0 var(--vg-space-1);
  color: var(--vg-text);
  font-family: var(--vg-font-display);
}

.page-header p,
.tutorial-steps p {
  margin: 0;
  color: var(--vg-text-muted);
}

.tutorial-steps {
  list-style: none;
  display: grid;
  gap: var(--vg-space-4);
  margin: 0;
  padding: 0;
  max-width: 760px;
}

.tutorial-steps li {
  display: flex;
  gap: var(--vg-space-4);
  padding: var(--vg-space-5);
  border: 1px solid var(--vg-border);
  border-radius: var(--vg-radius);
  background: var(--vg-surface);
}

.tutorial-steps span {
  width: 2rem;
  height: 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: var(--vg-radius-sm);
  background: var(--vg-grad-blue);
  color: white;
  font-weight: 700;
}

.tutorial-steps h3 {
  margin: 0 0 var(--vg-space-1);
  color: var(--vg-text);
  font-size: var(--vg-text-lg);
}
</style>
