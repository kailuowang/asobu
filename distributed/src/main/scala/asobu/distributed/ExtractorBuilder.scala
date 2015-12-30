package asobu.distributed

import asobu.dsl.Extractor
import shapeless.HList

trait ExtractorBuilder {
  //  object bind extends FieldPoly {
  //    implicit def rpb[T, K](implicit witness: Witness.Aux[K]): Case.Aux[FieldType[K, Read[T]], FieldType[K, T]] =
  //      atField(witness)(_(requestParams))
  //  }

  /**
   * generate an Extractor from extractors and FullRepr, compensate the missing fields with extractors from pathParams and queryParams
   * @param extractors
   * @tparam T
   * @tparam FullRepr
   * @tparam Extractors
   * @return
   */
  def extractor[T, FullRepr <: HList, Extractors <: HList](extractors: Extractors): Extractor[FullRepr]
}
