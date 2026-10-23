from django.http import JsonResponse
from django.core.exceptions import RequestDataTooBig

class ApiBoundary:
    def __init__(self, get_response): self.get_response = get_response
    def __call__(self, request):
        try:
            if int(request.META.get('CONTENT_LENGTH') or 0) > 16*1024:
                response = JsonResponse({'error': 'INVALID_BODY'}, status=413)
            elif request.method not in ('GET', 'HEAD') and request.content_type != 'application/json':
                response = JsonResponse({'error': 'JSON_REQUIRED'}, status=415)
            else: response = self.get_response(request)
        except (ValueError, RequestDataTooBig):
            response = JsonResponse({'error': 'INVALID_BODY'}, status=413)
        response['Cache-Control'] = 'no-store'
        response['Referrer-Policy'] = 'no-referrer'
        response['X-Content-Type-Options'] = 'nosniff'
        response['Content-Security-Policy'] = "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'"
        return response
