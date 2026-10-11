declare namespace API {
  type PictureUploadRequest = { id?: string | number; expectedRevision?: string | number; spaceId?: string | number; fileUrl?: string; picName?: string }
  type PictureUploadByBatchRequest = { searchText?: string; count?: number; namePrefix?: string }
  type BaseResponseInt_ = { code?: number; data?: number; message?: string }
  type BaseResponseBoolean_ = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseLoginUserVO_ = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong_ = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePagePicture_ = {
    code?: number
    data?: PagePicture_
    message?: string
  }

  type BaseResponsePagePictureVO_ = {
    code?: number
    data?: PagePictureVO_
    message?: string
  }

  type BaseResponsePageUserVO_ = {
    code?: number
    data?: PageUserVO_
    message?: string
  }

  type BaseResponsePicture_ = {
    code?: number
    data?: Picture
    message?: string
  }

  type BaseResponsePictureTagCategory_ = {
    code?: number
    data?: PictureTagCategory
    message?: string
  }

  type BaseResponsePictureVO_ = {
    code?: number
    data?: PictureVO
    message?: string
  }

  type BaseResponseString_ = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser_ = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO_ = {
    code?: number
    data?: UserVO
    message?: string
  }

  type DeleteRequest = {
    expectedRevision?: string | number
    id?: number | string
  }

  type getPictureByIdUsingGETParams = {
    /** id */
    id?: number | string
  }

  type getPictureVOByIdUsingGETParams = {
    /** id */
    id?: number | string
  }

  type getUserByIdUsingGETParams = {
    /** id */
    id?: number | string
  }

  type getUserVOByIdUsingGETParams = {
    /** id */
    id?: number | string
  }

  type LoginUserVO = {
    createTime?: string
    editTime?: string
    id?: number | string
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type PagePicture_ = {
    current?: number
    pages?: number
    records?: Picture[]
    size?: number
    total?: number
  }

  type PagePictureVO_ = {
    current?: number
    pages?: number
    records?: PictureVO[]
    size?: number
    total?: number
  }

  type PageUserVO_ = {
    current?: number
    pages?: number
    records?: UserVO[]
    size?: number
    total?: number
  }

  type Picture = {
    revision?: string | number
    thumbnailUrl?: string
    picColor?: string
    spaceId?: string | number
    category?: string
    createTime?: string
    editTime?: string
    id?: number | string
    introduction?: string
    isDelete?: number
    name?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: number | string
    tags?: string
    updateTime?: string
    url?: string
    userId?: number | string
  }

  type PictureEditRequest = {
    expectedRevision?: string | number
    category?: string
    id?: number | string
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureQueryRequest = {
    spaceId?: string | number
    nullSpaceId?: boolean
    category?: string
    current?: number
    id?: number | string
    introduction?: string
    name?: string
    pageSize?: number
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewerId?: number | string
    searchText?: string
    sortField?: string
    sortOrder?: string
    tags?: string[]
    userId?: number | string
  }

  type PictureReviewRequest = {
    expectedRevision?: string | number
    id?: number | string
    reviewMessage?: string
    reviewStatus?: number
  }

  type PictureTagCategory = {
    categoryList?: string[]
    tagList?: string[]
  }

  type PictureUpdateRequest = {
    expectedRevision?: string | number
    category?: string
    id?: number | string
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureVO = {
    revision?: string | number
    thumbnailUrl?: string
    picColor?: string
    spaceId?: string | number
    category?: string
    createTime?: string
    editTime?: string
    id?: number | string
    introduction?: string
    name?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: number
    picWidth?: number
    tags?: string[]
    updateTime?: string
    url?: string
    user?: UserVO
    userId?: number | string
  }

  type testDownloadFileUsingGETParams = {
    /** filepath */
    filepath?: string
  }

  type uploadPictureUsingPOSTParams = {
    expectedRevision?: string | number
    spaceId?: string | number
    id?: number | string
  }

  type User = {
    createTime?: string
    editTime?: string
    id?: number | string
    isDelete?: number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userPassword?: string
    userProfile?: string
    userRole?: string
  }

  type UserAddRequest = {
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    current?: number
    id?: number | string
    pageSize?: number
    sortField?: string
    sortOrder?: string
    userAccount?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    checkPassword?: string
    userAccount?: string
    userPassword?: string
  }

  type UserUpdateRequest = {
    id?: number | string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    createTime?: string
    id?: number | string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }
}
