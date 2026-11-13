import unittest
import datetime
from audit_java import packages_from_tree, scan, review_findings


class AuditTests(unittest.TestCase):
    def test_transitive_dependencies_included_but_local_root_excluded(self):
        tree = {'children': [{'groupId': 'a', 'artifactId': 'b', 'version': '1',
                             'children': [{'groupId': 'c', 'artifactId': 'd', 'version': '2'}]}]}
        self.assertEqual({('a:b', '1'), ('c:d', '2')}, packages_from_tree(tree))

    def test_findings_on_later_page_are_not_lost(self):
        def send(queries):
            if 'page_token' not in queries[0]:
                return {'results': [{'next_page_token': 'next'}]}
            return {'results': [{'vulns': [{'id': 'TEST-1'}]}]}
        self.assertEqual([('a:b', '1', 'TEST-1')], scan({('a:b', '1')}, send))

    def test_incomplete_response_is_not_clean(self):
        with self.assertRaises(ValueError):
            scan({('a:b', '1')}, lambda _: {'results': []})

    def test_repeated_pagination_is_rejected(self):
        with self.assertRaises(ValueError):
            scan({('a:b', '1')}, lambda _: {'results': [{'next_page_token': 'same'}]})

    def test_review_is_exact_and_expiry_is_enforced(self):
        today = datetime.date(2026, 10, 27)
        review = {'package': 'a:b', 'version': '1', 'id': 'TEST-1',
                  'reason': 'Not reachable; source reviewed', 'expires': '2026-11-27'}
        assessed = review_findings([('a:b', '2', 'TEST-1')], [review], today)
        self.assertIsNone(assessed[0][1])
        review['expires'] = '2026-10-26'
        with self.assertRaises(ValueError):
            review_findings([('a:b', '1', 'TEST-1')], [review], today)


if __name__ == '__main__':
    unittest.main()
