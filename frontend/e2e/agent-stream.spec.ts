import { expect, test } from "@playwright/test";
import { sseEvents } from "../src/app/assistant/agent-api";

const STEP = {
  type: "step",
  tool: "search_flights",
  args: {},
  status: "ok",
};
const TURN = {
  type: "turn",
  turn: { reply: "Đặt xong", trace: [], cards: [] },
};

function stream(...chunks: Uint8Array[]) {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk);
      controller.close();
    },
  });
}

test("sự kiện bị cắt giữa hai chunk, kể cả giữa một chữ có dấu", async () => {
  const bytes = new TextEncoder().encode(
    `data: ${JSON.stringify(STEP)}\n\ndata: ${JSON.stringify(TURN)}\n\n`,
  );
  const cut = bytes.indexOf(0xc4) + 1; // giữa hai byte của "Đ"
  const events = [];
  for await (const event of sseEvents(
    stream(bytes.slice(0, 20), bytes.slice(20, cut), bytes.slice(cut)),
  ))
    events.push(event);
  expect(events).toEqual([STEP, TURN]);
});
