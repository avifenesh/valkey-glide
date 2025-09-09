#!/usr/bin/env python3
"""
cluster_failures.py

Generate clustered summaries of failing JUnit test cases for the JNI-java integration test suite.

Outputs:
  - integTest/FAILURE_CLUSTERS.md : Human friendly markdown summary
  - integTest/build/failure_clusters.json : Machine readable structured data (optional consumption by CI)

Invocation:
  python3 JNI-java/integTest/scripts/cluster_failures.py \
      --results-dir JNI-java/integTest/build/test-results/test

Exit Codes:
  0 success (even if there are failures; this is an analysis tool)
  2 unable to locate results

Design Notes:
  * We purposely avoid fuzzy / edit-distance merging initially for determinism.
  * Normalization strips volatile tokens (object hex ids, numbers optionally) to create stable signatures.
  * Adjust NORMALIZE_NUMBERS / MAX_SIGNATURE_LEN as needed.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import List, Dict, Tuple

HEX_OBJ = re.compile(r"@[0-9a-fA-F]{6,16}")
BYTE_ARRAY_ADDR = re.compile(r"byte\[\] \d+")
ABS_PATH = re.compile(r"/[^\s]+")  # coarse removal of absolute paths
MULTI_WS = re.compile(r"\s+")
NUM = re.compile(r"\d+")
BRACKET_INDEX = re.compile(r"\[[0-9]+] ")

NORMALIZE_NUMBERS = False  # set True if numeric noise dominates signatures
MAX_SIGNATURE_LEN = 180

@dataclass
class FailureRecord:
    suite: str
    test: str
    signature: str
    raw_message: str
    count: int = 1


def normalize(message: str) -> str:
    if not message:
        return "<empty>"
    msg = message.strip()
    # Take only first line (eliminate stack trace variance) but keep some context if line very short
    first_line, *_rest = msg.splitlines() or [msg]
    msg = first_line
    msg = HEX_OBJ.sub('@<id>', msg)
    msg = BYTE_ARRAY_ADDR.sub('byte[] <n>', msg)
    msg = ABS_PATH.sub('/<path>', msg)
    msg = BRACKET_INDEX.sub('', msg)
    if NORMALIZE_NUMBERS:
        msg = NUM.sub('#', msg)
    msg = msg.lower()
    msg = MULTI_WS.sub(' ', msg)
    msg = msg[:MAX_SIGNATURE_LEN]
    return msg


def parse_results(results_dir: Path) -> List[FailureRecord]:
    if not results_dir.exists():
        raise FileNotFoundError(f"Results directory not found: {results_dir}")
    records: List[FailureRecord] = []
    xml_paths = sorted(results_dir.glob('TEST-*.xml'))
    for xml_path in xml_paths:
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError as e:
            print(f"WARN: Skipping unparsable XML {xml_path}: {e}", file=sys.stderr)
            continue
        suite = tree.getroot().attrib.get('name', xml_path.stem)
        # Iterate over direct child testcases (Gradle/JUnit XML structure places all testcases directly under testsuite)
        for tc in tree.getroot().findall('testcase'):
            failure = tc.find('failure')
            if failure is not None:
                node = failure  # do not rely on truthiness; empty element (no children) still valid
            else:
                error = tc.find('error')
                node = error
            if node is None:
                continue
            raw_msg = node.attrib.get('message') if 'message' in node.attrib else ''
            if not raw_msg:
                # Fall back to element text (trim large stack traces later in normalize)
                raw_msg = (node.text or '').strip()
            signature = normalize(raw_msg)
            test_name = tc.attrib.get('name', '<unknown>')
            records.append(FailureRecord(suite=suite, test=test_name, signature=signature, raw_message=raw_msg))
    # Debug fallback: if records unexpectedly empty but any XML contains <failure>, warn.
    if not records:
        sample = next((p for p in xml_paths if '<failure' in p.read_text()), None)
        if sample:
            print(f"WARN: Detected <failure> tags in {sample.name} but no records parsed. (If suite is fully green ignore.)", file=sys.stderr)
    return records


def cluster_failures(records: List[FailureRecord]) -> Dict[str, List[FailureRecord]]:
    clusters: Dict[str, List[FailureRecord]] = {}
    for r in records:
        clusters.setdefault(r.signature, []).append(r)
    return clusters


def write_markdown(clusters: Dict[str, List[FailureRecord]], total_failures: int, out_path: Path):
    lines: List[str] = []
    timestamp = dt.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')
    sorted_clusters = sorted(clusters.items(), key=lambda kv: len(kv[1]), reverse=True)
    lines.append(f"# Failure Clusters (Generated {timestamp})\n")
    lines.append(f"Total failing test cases: {total_failures}")
    lines.append(f"Unique normalized clusters: {len(sorted_clusters)}\n")
    lines.append('| Rank | Count | Percent | Signature |')
    lines.append('|------|-------|---------|-----------|')
    for idx, (sig, recs) in enumerate(sorted_clusters, start=1):
        pct = (len(recs) / total_failures * 100) if total_failures else 0
        lines.append(f"| {idx} | {len(recs)} | {pct:5.1f}% | {sig.replace('|','\\|')} |")
    lines.append('\n---\n')
    for idx, (sig, recs) in enumerate(sorted_clusters, start=1):
        lines.append(f"## Cluster {idx} ({len(recs)})")
        lines.append(f"Signature: {sig}")
        # Representative example
        example = recs[0]
        lines.append(f"Representative: {example.suite} :: {example.test}")
        # List up to first 10 examples
        ex_list = recs[:10]
        lines.append('Examples:')
        for r in ex_list:
            lines.append(f"  - {r.suite} :: {r.test}")
        lines.append('')
    out_path.write_text('\n'.join(lines), encoding='utf-8')


def write_json(clusters: Dict[str, List[FailureRecord]], out_path: Path):
    serializable = {
        'generated_at': dt.datetime.utcnow().isoformat() + 'Z',
        'clusters': [
            {
                'signature': sig,
                'count': len(recs),
                'examples': [{'suite': r.suite, 'test': r.test} for r in recs[:20]],
            }
            for sig, recs in sorted(clusters.items(), key=lambda kv: len(kv[1]), reverse=True)
        ],
    }
    out_path.write_text(json.dumps(serializable, indent=2), encoding='utf-8')


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description='Cluster failing JUnit tests by normalized message.')
    parser.add_argument('--results-dir', default='JNI-java/integTest/build/test-results/test', help='Path to JUnit results directory.')
    parser.add_argument('--md-out', default='JNI-java/integTest/FAILURE_CLUSTERS.md', help='Output markdown path.')
    parser.add_argument('--json-out', default='JNI-java/integTest/build/failure_clusters.json', help='Output JSON path.')
    args = parser.parse_args(argv)

    results_dir = Path(args.results_dir)
    try:
        records = parse_results(results_dir)
    except FileNotFoundError as e:
        print(str(e), file=sys.stderr)
        return 2
    if not records:
        print("No failing test cases found (all green or no XML failures).", file=sys.stderr)
        total_failures = 0
        clusters = {}
    else:
        clusters = cluster_failures(records)
        total_failures = len(records)

    write_markdown(clusters, total_failures, Path(args.md_out))
    # Ensure directory exists for json
    json_path = Path(args.json_out)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    write_json(clusters, json_path)

    print(f"Wrote {len(clusters)} clusters for {total_failures} failing cases.")
    return 0

if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1:]))
