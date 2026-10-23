import test from "node:test"
import assert from "node:assert/strict"
import {qaPreparationRows,qaTimingSeconds} from "./qaPreparationTiming.js"

test("preparation bottlenecks sort by mean and retain measured zero without inventing missing stages",()=>{
  const rows=qaPreparationRows({TIME_INTERPRETATION:{count:2,average:900},CONTEXT_BUILD:{count:2,average:100},
    CONTEXT_MEMORY_RECALL:{count:2,average:0},TARGET_TOKEN_COUNT:{count:0,average:0}})
  assert.deepEqual(rows.map(row=>row.key),["TIME_INTERPRETATION","CONTEXT_BUILD","CONTEXT_MEMORY_RECALL"])
  assert.equal(qaPreparationRows(null).length,0)
  assert.equal(qaTimingSeconds(null),"미측정")
  assert.equal(qaTimingSeconds(0),"0.000초")
})
