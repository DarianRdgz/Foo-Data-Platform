import { feature } from "topojson-client";

async function loadTopology(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load boundary topology: ${url}`);
  }
  return response.json();
}

export async function loadStateBoundaries() {
  const topology = await loadTopology("/map-boundaries/states-10m.json");
  return feature(topology, topology.objects.states);
}

export async function loadCountyBoundaries() {
  const topology = await loadTopology("/map-boundaries/counties-10m.json");
  return feature(topology, topology.objects.counties);
}
