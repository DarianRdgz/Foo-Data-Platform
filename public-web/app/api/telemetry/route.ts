import { NextResponse } from "next/server";

interface TelemetryPayload {
  type: "pageview" | "client_error";
  path: string;
  timestamp: string;
  message?: string;
  source?: string | null;
}

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const body: TelemetryPayload = await request.json();

    if (!body.type || !body.path || !body.timestamp) {
      return NextResponse.json({ error: "Invalid payload" }, { status: 400 });
    }

    console.log("[telemetry]", JSON.stringify(body));

    return new NextResponse(null, { status: 204 });
  } catch {
    return NextResponse.json({ error: "Bad request" }, { status: 400 });
  }
}