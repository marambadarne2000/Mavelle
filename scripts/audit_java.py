"""Query OSV for resolved Maven dependencies; never treat an incomplete scan as clean."""
import argparse
import datetime
import json
from pathlib import Path
import urllib.request


def packages_from_tree(tree):
    packages = set()

    def visit(node):
        for child in node.get('children', []):
            packages.add((child['groupId'] + ':' + child['artifactId'], child['version']))
            visit(child)

    visit(tree)  # The local application itself is not a published Maven package.
    if not packages:
        raise ValueError('Dependency inventory is empty')
    return packages


def request_osv(queries):
    request = urllib.request.Request(
        'https://api.osv.dev/v1/querybatch',
        data=json.dumps({'queries': queries}).encode(),
        headers={'Content-Type': 'application/json', 'User-Agent': 'Maramigo-dependency-review'},
        method='POST',
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def scan(packages, send=request_osv):
    findings = set()
    ordered = sorted(packages)
    for start in range(0, len(ordered), 100):
        pending = [(name, version, None) for name, version in ordered[start:start + 100]]
        seen_tokens = set()
        while pending:
            queries = []
            for name, version, token in pending:
                query = {'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
                if token:
                    query['page_token'] = token
                queries.append(query)
            response = send(queries)
            results = response.get('results')
            if not isinstance(results, list) or len(results) != len(pending):
                raise ValueError('Incomplete OSV response')
            next_page = []
            for (name, version, _), result in zip(pending, results):
                if not isinstance(result, dict) or 'error' in result:
                    raise ValueError('Invalid OSV package result')
                vulns = result.get('vulns', [])
                if not isinstance(vulns, list):
                    raise ValueError('Invalid OSV vulnerabilities')
                for vuln in vulns:
                    identifier = vuln['id']
                    if not isinstance(identifier, str) or not identifier:
                        raise ValueError('Invalid vulnerability identifier')
                    findings.add((name, version, identifier))
                token = result.get('next_page_token')
                if token:
                    key = (name, version, token)
                    if key in seen_tokens:
                        raise ValueError('Repeated OSV pagination token')
                    seen_tokens.add(key)
                    next_page.append(key)
            pending = next_page
    return sorted(findings)


def review_findings(findings, reviews, today):
    active = {}
    for review in reviews:
        key = (review['package'], review['version'], review['id'])
        if key in active or not review['reason'].strip():
            raise ValueError('Invalid or duplicate dependency review')
        if datetime.date.fromisoformat(review['expires']) < today:
            raise ValueError('Dependency review expired; reassess before passing')
        active[key] = review
    return [(finding, active.get(finding)) for finding in findings]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', nargs='+', required=True)
    parser.add_argument('--report', required=True)
    parser.add_argument('--reviews')
    args = parser.parse_args()
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'source': 'https://api.osv.dev/v1/querybatch', 'status': 'error'}
    try:
        packages = set()
        for inventory in args.inventory:
            packages.update(packages_from_tree(json.loads(Path(inventory).read_text(encoding='utf-8-sig'))))
        findings = scan(packages)
        reviews = json.loads(Path(args.reviews).read_text(encoding='utf-8-sig')) if args.reviews else []
        assessed = review_findings(findings, reviews, datetime.datetime.now(datetime.timezone.utc).date())
        unreviewed = [f for f, review in assessed if review is None]
        report.update(status='findings' if unreviewed else 'reviewed' if findings else 'clean', packagesChecked=len(packages),
                      packages=[{'name': n, 'version': v} for n, v in sorted(packages)],
                      findings=[{'package': n, 'version': v, 'id': i,
                                 'url': 'https://osv.dev/vulnerability/' + i,
                                 'review': review} for (n, v, i), review in assessed])
    except Exception as error:
        report['errorType'] = type(error).__name__
        print('ERROR: Java dependency audit incomplete; inspect inventories/network and rerun.')
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    if report['status'] == 'clean':
        print(f"PASS: OSV returned no known findings for {report['packagesChecked']} resolved Maven package versions.")
        return 0
    if report['status'] in ('findings', 'reviewed'):
        for finding in report['findings']:
            label = 'REVIEWED (not removed)' if finding['review'] else 'FINDING'
            print(f"{label}: {finding['package']}@{finding['version']} {finding['url']}")
        if report['status'] == 'reviewed':
            print(f"PASS WITH REVIEWS: {report['packagesChecked']} packages checked; {len(report['findings'])} findings retained with exact, time-limited reviews.")
            return 0
        return 1
    return 2


if __name__ == '__main__':
    raise SystemExit(main())
