# DefaultApi

All URIs are relative to *http://localhost:8083/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getActiveProfiles**](#getactiveprofiles) | **GET** /users/{userId}/profiles/active | Profiles active during a timeframe|
|[**getAgp**](#getagp) | **GET** /users/{userId}/analytics/agp | Ambulatory Glucose Profile — hourly percentiles|
|[**getHba1c**](#gethba1c) | **GET** /users/{userId}/analytics/hba1c | HbA1c estimation and time-in-range for a timeframe|
|[**getTimeline**](#gettimeline) | **GET** /users/{userId}/timeline | Combined measures and treatments for a timeframe|

# **getActiveProfiles**
> ProfilesResponse getActiveProfiles()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getActiveProfiles(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**ProfilesResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Active profiles during the given timeframe |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getAgp**
> AgpResponse getAgp()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getAgp(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**AgpResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | AGP hourly data (24 buckets) |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getHba1c**
> Hba1cResponse getHba1c()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getHba1c(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**Hba1cResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | HbA1c analytics result |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getTimeline**
> TimelineResponse getTimeline()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; //Start of timeframe (ISO-8601 datetime) (default to undefined)
let to: string; //End of timeframe (ISO-8601 datetime) (default to undefined)

const { status, data } = await apiInstance.getTimeline(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] | Start of timeframe (ISO-8601 datetime) | defaults to undefined|
| **to** | [**string**] | End of timeframe (ISO-8601 datetime) | defaults to undefined|


### Return type

**TimelineResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Combined timeline data |  -  |
|**401** | JWT missing or invalid |  -  |
|**403** | Authenticated user lacks access to this resource |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

