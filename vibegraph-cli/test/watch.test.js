import { test } from "node:test";
import assert from "node:assert/strict";

import { createSerializedPushRunner } from "../lib/watch.js";

test("serialized watch runner schedules a follow-up when changes arrive during a push", async () => {
  let releaseFirstPush;
  let taskCalls = 0;
  let pendingSchedules = 0;
  const firstPush = new Promise((resolve) => { releaseFirstPush = resolve; });
  const run = createSerializedPushRunner(async () => {
    taskCalls += 1;
    if (taskCalls === 1) await firstPush;
  }, () => { pendingSchedules += 1; });

  const activePush = run();
  await Promise.resolve();
  await run();
  assert.equal(taskCalls, 1);

  releaseFirstPush();
  await activePush;
  assert.equal(pendingSchedules, 1);
});
