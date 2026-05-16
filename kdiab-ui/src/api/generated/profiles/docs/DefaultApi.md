# DefaultApi

All URIs are relative to *http://localhost:8080/api/v1*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**acceptProposedProfile**](#acceptproposedprofile) | **POST** /users/{userId}/profiles/{profileId}/accept | Accept a proposed profile|
|[**activateProfile**](#activateprofile) | **POST** /users/{userId}/profiles/{profileId}/activate | Activate a profile|
|[**createInsulin**](#createinsulin) | **POST** /insulins | Create a new insulin|
|[**createProfile**](#createprofile) | **POST** /users/{userId}/profiles | Create a new profile|
|[**deleteInsulin**](#deleteinsulin) | **DELETE** /insulins/{id} | Delete a specific insulin|
|[**deleteProfile**](#deleteprofile) | **DELETE** /users/{userId}/profiles/{profileId} | Archive a specific profile|
|[**deleteProfiles**](#deleteprofiles) | **DELETE** /users/{userId}/profiles | Delete all profiles for a user|
|[**deleteSegment**](#deletesegment) | **DELETE** /users/{userId}/profiles/{profileId}/{segmentType}/{startTime} | Delete a specific segment from a profile|
|[**getInsulins**](#getinsulins) | **GET** /insulins | Get all insulins|
|[**getProfile**](#getprofile) | **GET** /users/{userId}/profiles/{profileId} | Get a specific profile by ID|
|[**getProfileHistory**](#getprofilehistory) | **GET** /users/{userId}/profiles/history | Get profile history for a user|
|[**listProfiles**](#listprofiles) | **GET** /users/{userId}/profiles | List all profiles for a user|
|[**rejectProposedProfile**](#rejectproposedprofile) | **POST** /users/{userId}/profiles/{profileId}/reject | Reject a proposed profile|
|[**updateInsulin**](#updateinsulin) | **PUT** /insulins/{id} | Update a specific insulin|
|[**updateProfile**](#updateprofile) | **PUT** /users/{userId}/profiles/{profileId} | Update a profile|

# **acceptProposedProfile**
> Profile acceptProposedProfile()

Sets a PROPOSED profile to ACTIVE and archives the previously active profile.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)

const { status, data } = await apiInstance.acceptProposedProfile(
    userId,
    profileId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Profile accepted |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **activateProfile**
> Profile activateProfile()

Sets the profile status to ACTIVE and archives the previously active profile.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)

const { status, data } = await apiInstance.activateProfile(
    userId,
    profileId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Profile activated |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **createInsulin**
> Insulin createInsulin(createInsulinRequest)

Adds a new insulin type to the shared catalogue. Requires ADMIN role.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    CreateInsulinRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let createInsulinRequest: CreateInsulinRequest; //

const { status, data } = await apiInstance.createInsulin(
    createInsulinRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createInsulinRequest** | **CreateInsulinRequest**|  | |


### Return type

**Insulin**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Created insulin |  -  |
|**400** | Invalid name (blank or too long) |  -  |
|**401** | Unauthorized |  -  |
|**409** | Insulin with that name already exists |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **createProfile**
> Profile createProfile(createProfileRequest)

Creates a new profile in DRAFT status. A doctor may create a PROPOSED profile for a patient by supplying a proposalReason.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    CreateProfileRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let createProfileRequest: CreateProfileRequest; //

const { status, data } = await apiInstance.createProfile(
    userId,
    createProfileRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createProfileRequest** | **CreateProfileRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Profile created |  -  |
|**400** | Validation error |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteInsulin**
> deleteInsulin()

Permanently removes an insulin type from the catalogue. Requires ADMIN role.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let id: string; // (default to undefined)

const { status, data } = await apiInstance.deleteInsulin(
    id
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **id** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | Insulin deleted |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden — admin role required |  -  |
|**404** | Insulin not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteProfile**
> deleteProfile()

Sets the profile status to ARCHIVED. Does not delete data.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)

const { status, data } = await apiInstance.deleteProfile(
    userId,
    profileId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | Profile deleted |  -  |
|**404** | Profile not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteProfiles**
> deleteProfiles()

Permanently removes all profiles for a user. Restricted to ADMIN role. Use with caution — this is irreversible.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)

const { status, data } = await apiInstance.deleteProfiles(
    userId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | All profiles deleted |  -  |
|**404** | User not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteSegment**
> Profile deleteSegment()

Removes a single time segment (basal, ICR, ISF, or target) from a profile by its start time. Triggers copy-on-write if the profile is ACTIVE.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)
let segmentType: 'basal' | 'icr' | 'isf' | 'targets'; // (default to undefined)
let startTime: string; // (default to undefined)

const { status, data } = await apiInstance.deleteSegment(
    userId,
    profileId,
    segmentType,
    startTime
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|
| **segmentType** | [**&#39;basal&#39; | &#39;icr&#39; | &#39;isf&#39; | &#39;targets&#39;**]**Array<&#39;basal&#39; &#124; &#39;icr&#39; &#124; &#39;isf&#39; &#124; &#39;targets&#39;>** |  | defaults to undefined|
| **startTime** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Segment deleted, returns updated profile |  -  |
|**404** | Profile or segment not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getInsulins**
> Array<Insulin> getInsulins()

Returns all insulin types available for selection when creating or updating a profile.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

const { status, data } = await apiInstance.getInsulins();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**Array<Insulin>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | A list of insulins |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getProfile**
> Profile getProfile()

Returns the full details of a single profile, including all basal, ICR, ISF, and target segments.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)

const { status, data } = await apiInstance.getProfile(
    userId,
    profileId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Profile details |  -  |
|**404** | Profile not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getProfileHistory**
> Array<Profile> getProfileHistory()

Returns all profiles (active and archived) that were valid within the given time range, ordered by validFrom descending. Used to reconstruct which profile was active at any point in time.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let from: string; // (default to undefined)
let to: string; // (default to undefined)

const { status, data } = await apiInstance.getProfileHistory(
    userId,
    from,
    to
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **from** | [**string**] |  | defaults to undefined|
| **to** | [**string**] |  | defaults to undefined|


### Return type

**Array<Profile>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | List of historical profiles |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listProfiles**
> PagedProfilesResponse listProfiles()

Returns a paginated list of profiles for the given user, optionally filtered by status. Accessible by the user themselves, their assigned doctors, and admins.

### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let page: number; // (optional) (default to 0)
let size: number; // (optional) (default to 50)
let status: Array<'DRAFT' | 'PROPOSED' | 'ACTIVE' | 'ARCHIVED'>; //Filter by profile status. Multiple values allowed. If omitted, all statuses are returned. (optional) (default to undefined)

const { status, data } = await apiInstance.listProfiles(
    userId,
    page,
    size,
    status
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **userId** | [**string**] |  | defaults to undefined|
| **page** | [**number**] |  | (optional) defaults to 0|
| **size** | [**number**] |  | (optional) defaults to 50|
| **status** | **Array<&#39;DRAFT&#39; &#124; &#39;PROPOSED&#39; &#124; &#39;ACTIVE&#39; &#124; &#39;ARCHIVED&#39;>** | Filter by profile status. Multiple values allowed. If omitted, all statuses are returned. | (optional) defaults to undefined|


### Return type

**PagedProfilesResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | A paginated list of profiles |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **rejectProposedProfile**
> Profile rejectProposedProfile()

Sets a PROPOSED profile to ARCHIVED. An optional reason may be provided.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    RejectProfileRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)
let rejectProfileRequest: RejectProfileRequest; // (optional)

const { status, data } = await apiInstance.rejectProposedProfile(
    userId,
    profileId,
    rejectProfileRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **rejectProfileRequest** | **RejectProfileRequest**|  | |
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Profile rejected |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **updateInsulin**
> Insulin updateInsulin(updateInsulinRequest)

Renames an existing insulin type. Requires ADMIN role.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    UpdateInsulinRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let id: string; // (default to undefined)
let updateInsulinRequest: UpdateInsulinRequest; //

const { status, data } = await apiInstance.updateInsulin(
    id,
    updateInsulinRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **updateInsulinRequest** | **UpdateInsulinRequest**|  | |
| **id** | [**string**] |  | defaults to undefined|


### Return type

**Insulin**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Updated insulin |  -  |
|**400** | Invalid name (blank or too long) |  -  |
|**401** | Unauthorized |  -  |
|**403** | Forbidden — admin role required |  -  |
|**404** | Insulin not found |  -  |
|**409** | Insulin with that name already exists |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **updateProfile**
> Profile updateProfile(profile)

Updates an existing profile. If active, creates a new version.

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    Profile
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let userId: string; // (default to undefined)
let profileId: string; // (default to undefined)
let profile: Profile; //

const { status, data } = await apiInstance.updateProfile(
    userId,
    profileId,
    profile
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **profile** | **Profile**|  | |
| **userId** | [**string**] |  | defaults to undefined|
| **profileId** | [**string**] |  | defaults to undefined|


### Return type

**Profile**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | Profile updated |  -  |
|**404** | Profile not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

